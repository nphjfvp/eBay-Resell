package de.dealcheck.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import de.dealcheck.app.BuildConfig
import de.dealcheck.app.analysis.DealCheckException
import de.dealcheck.app.analysis.OpenRouterClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ReleaseInfo(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(override val release: ReleaseInfo) : UpdateState
    data class Downloading(override val release: ReleaseInfo, val progress: Float) : UpdateState
    data class ReadyToInstall(override val release: ReleaseInfo, val file: File) : UpdateState
    data class Error(val message: String, override val release: ReleaseInfo? = null) : UpdateState

    /** Die betroffene Version, falls es um ein konkretes Update geht. */
    val release: ReleaseInfo? get() = null
}

/**
 * Prüft die GitHub-Releases auf eine neuere Version, lädt die APK herunter und
 * übergibt sie an den Android-Paketinstaller.
 */
class UpdateManager(
    private val context: Context,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val apiBase: String = "https://api.github.com",
    private val installedVersion: String = BuildConfig.VERSION_NAME,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val dir = File(context.cacheDir, "updates")

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Version, deren Hinweis der Nutzer mit „Später“ weggeklickt hat (nur bis zum App-Neustart). */
    private val _dismissed = MutableStateFlow<String?>(null)
    val dismissed: StateFlow<String?> = _dismissed.asStateFlow()

    private var job: Job? = null

    val currentVersion: String get() = installedVersion

    /** Automatische Prüfung beim Start, höchstens alle paar Stunden. */
    fun checkOnStartup() {
        val last = prefs.getLong(KEY_LAST_CHECK, 0)
        if (System.currentTimeMillis() - last < AUTO_CHECK_INTERVAL_MS) return
        check(silent = true)
    }

    /** [silent]: Fehler und „aktuell“ nicht anzeigen (automatische Prüfung). */
    fun check(silent: Boolean = false) {
        if (job?.isActive == true) return
        job = scope.launch {
            if (!silent) _state.value = UpdateState.Checking
            try {
                val release = fetchLatest()
                prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
                _state.value = when {
                    release != null && Version.isNewer(release.version, installedVersion) -> UpdateState.Available(release)
                    silent -> UpdateState.Idle
                    else -> UpdateState.UpToDate
                }
            } catch (e: DealCheckException) {
                _state.value = if (silent) UpdateState.Idle else UpdateState.Error(e.message.orEmpty())
            }
        }
    }

    /** „Später“: Hinweis für diese Version ausblenden (Download läuft ggf. weiter). */
    fun dismiss() {
        val s = _state.value
        s.release?.let { _dismissed.value = it.version }
        if (s is UpdateState.Error) _state.value = s.release?.let { UpdateState.Available(it) } ?: UpdateState.Idle
        if (s is UpdateState.UpToDate) _state.value = UpdateState.Idle
    }

    /** Hinweis wieder anzeigen (z. B. aus den Einstellungen). */
    fun show() {
        _dismissed.value = null
    }

    fun download(release: ReleaseInfo) {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = UpdateState.Downloading(release, 0f)
            try {
                val file = downloadApk(release)
                _state.value = UpdateState.ReadyToInstall(release, file)
                install(file)
            } catch (e: DealCheckException) {
                _state.value = UpdateState.Error(e.message.orEmpty(), release)
            }
        }
    }

    /** Darf die App Pakete installieren? Ab Android 8 muss der Nutzer das einmalig erlauben. */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Startet den Paketinstaller; fehlt die Erlaubnis, öffnet sich zuerst die Einstellung. */
    fun install(file: File) {
        if (!canInstall()) {
            openInstallPermissionSettings()
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    internal suspend fun fetchLatest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$apiBase/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "DealCheck-Android")
            .build()
        val (code, body) = try {
            http.newCall(request).execute().use { it.code to it.body?.string().orEmpty() }
        } catch (e: IOException) {
            throw OpenRouterClient.mapIoError(e)
        }
        when (code) {
            200 -> parseRelease(body)
            404 -> null // noch kein Release veröffentlicht
            403, 429 -> throw DealCheckException("GitHub hat zu viele Anfragen gemeldet. Bitte später erneut versuchen.")
            else -> throw DealCheckException("Update-Prüfung fehlgeschlagen (GitHub-Code $code).")
        }
    }

    private suspend fun downloadApk(release: ReleaseInfo): File = withContext(Dispatchers.IO) {
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "DealCheck-${release.version}.apk")
        val request = Request.Builder().url(release.apkUrl).header("User-Agent", "DealCheck-Android").build()
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw DealCheckException("Download fehlgeschlagen (Code ${response.code}).")
                val body = response.body ?: throw DealCheckException("Download fehlgeschlagen (leere Antwort).")
                val total = body.contentLength().takeIf { it > 0 } ?: release.apkSize
                var read = 0L
                var lastReported = 0f
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (isActive) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            read += n
                            if (total > 0) {
                                val p = read.toFloat() / total
                                if (p - lastReported >= 0.01f) {
                                    lastReported = p
                                    _state.update { s -> if (s is UpdateState.Downloading) s.copy(progress = p) else s }
                                }
                            }
                        }
                    }
                }
                if (release.apkSize > 0 && read != release.apkSize) {
                    target.delete()
                    throw DealCheckException("Download unvollständig. Bitte erneut versuchen.")
                }
            }
        } catch (e: IOException) {
            target.delete()
            throw OpenRouterClient.mapIoError(e)
        }
        target
    }

    companion object {
        private const val KEY_LAST_CHECK = "last_check"
        private const val AUTO_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

        internal fun parseRelease(json: String): ReleaseInfo? {
            val obj = try {
                JSONObject(json)
            } catch (e: Exception) {
                throw DealCheckException("Unerwartete Antwort von GitHub.", e)
            }
            if (obj.optBoolean("draft") || obj.optBoolean("prerelease")) return null
            val assets = obj.optJSONArray("assets") ?: return null
            val apk = (0 until assets.length())
                .mapNotNull { assets.optJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                ?: return null
            return ReleaseInfo(
                version = obj.optString("tag_name").removePrefix("v"),
                notes = obj.optString("body").trim(),
                apkUrl = apk.optString("browser_download_url"),
                apkSize = apk.optLong("size", 0),
            )
        }
    }
}
