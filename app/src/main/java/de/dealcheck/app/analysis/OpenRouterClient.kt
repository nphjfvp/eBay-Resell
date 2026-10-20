package de.dealcheck.app.analysis

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/** Fehler mit verständlicher deutscher Meldung für die Oberfläche. */
class DealCheckException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class AnalysisResult(val analysis: DealAnalysis, val rawJson: String, val model: String)

class OpenRouterClient(
    private val http: OkHttpClient = defaultHttpClient(),
    private val endpoint: String = ENDPOINT,
) {

    /**
     * Schickt die Bilder (bereits als JPEG-Base64) an OpenRouter und liefert die geparste Analyse.
     * Ist die Antwort kein gültiges JSON, wird genau einmal nachgefragt.
     */
    suspend fun analyze(
        apiKey: String,
        model: String,
        webSearch: Boolean,
        imagesBase64: List<String>,
        note: String,
    ): AnalysisResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw DealCheckException("Kein API-Key eingetragen. Bitte unter „Einstellungen“ deinen OpenRouter-Key eintragen.")
        }
        if (imagesBase64.isEmpty()) throw DealCheckException("Bitte mindestens ein Bild hinzufügen.")

        val userContent = JSONArray().apply {
            put(JSONObject().put("type", "text").put("text", Prompts.user(imagesBase64.size, note)))
            imagesBase64.forEach { b64 ->
                put(
                    JSONObject()
                        .put("type", "image_url")
                        .put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$b64")),
                )
            }
        }
        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", Prompts.SYSTEM))
            put(JSONObject().put("role", "user").put("content", userContent))
        }

        val first = chat(apiKey, model, webSearch, messages)
        try {
            AnalysisResult(AnalysisParser.parse(first), AnalysisParser.extractJson(first), model)
        } catch (e: AnalysisParseException) {
            messages.put(JSONObject().put("role", "assistant").put("content", first))
            messages.put(JSONObject().put("role", "user").put("content", Prompts.RETRY))
            val second = chat(apiKey, model, webSearch, messages)
            try {
                AnalysisResult(AnalysisParser.parse(second), AnalysisParser.extractJson(second), model)
            } catch (e2: AnalysisParseException) {
                throw DealCheckException(
                    "Das Modell hat zweimal keine auswertbare Antwort geliefert. " +
                        "Versuch es erneut oder wähle in den Einstellungen ein anderes Modell.",
                    e2,
                )
            }
        }
    }

    private fun chat(apiKey: String, model: String, webSearch: Boolean, messages: JSONArray): String {
        val body = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("temperature", 0.2)
            if (webSearch) put("plugins", JSONArray().put(JSONObject().put("id", "web")))
        }
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .header("HTTP-Referer", "https://github.com/nphjfvp/eBay-Resell")
            .header("X-Title", "DealCheck")
            .post(body.toString().toRequestBody(JSON))
            .build()

        val (code, text) = try {
            http.newCall(request).execute().use { it.code to it.body?.string().orEmpty() }
        } catch (e: IOException) {
            throw mapIoError(e)
        }

        if (code !in 200..299) throw httpError(code, errorMessage(text))

        val json = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw DealCheckException("Unerwartete Antwort vom Server (kein JSON).", e)
        }
        // OpenRouter liefert Fehler teils mit Status 200 im Feld "error"
        json.optJSONObject("error")?.let { err ->
            throw httpError(err.optInt("code", 0), err.optString("message"))
        }
        val content = json.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.opt("content")
        return when (content) {
            is String -> content
            // manche Modelle liefern content als Liste von Teilen
            is JSONArray -> (0 until content.length()).joinToString("") {
                content.optJSONObject(it)?.optString("text").orEmpty()
            }
            else -> ""
        }.ifBlank { throw DealCheckException("Das Modell hat eine leere Antwort geliefert. Bitte erneut versuchen.") }
    }

    private fun errorMessage(body: String): String = try {
        JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
    } catch (_: Exception) {
        ""
    }

    companion object {
        const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(150, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .build()

        internal fun mapIoError(e: IOException): DealCheckException = when (e) {
            is UnknownHostException, is ConnectException ->
                DealCheckException("Keine Internetverbindung. Bitte prüfe WLAN oder mobile Daten.", e)
            is SocketTimeoutException ->
                DealCheckException("Zeitüberschreitung: Der Server hat zu lange nicht geantwortet. Bitte erneut versuchen.", e)
            is InterruptedIOException ->
                DealCheckException("Zeitüberschreitung: Die Analyse hat zu lange gedauert. Bitte erneut versuchen.", e)
            is SSLException ->
                DealCheckException("Sichere Verbindung fehlgeschlagen. Bitte Netzwerk prüfen.", e)
            else -> DealCheckException("Netzwerkfehler: ${e.message ?: e.javaClass.simpleName}", e)
        }

        internal fun httpError(code: Int, message: String): DealCheckException {
            val detail = message.takeIf { it.isNotBlank() }?.let { "\n\nDetails: $it" }.orEmpty()
            val text = when (code) {
                400 -> "Die Anfrage wurde abgelehnt. Unterstützt das gewählte Modell Bilder? " +
                    "Prüfe den Modellnamen in den Einstellungen."
                401 -> "API-Key ungültig. Bitte prüfe deinen OpenRouter-Key in den Einstellungen."
                402 -> "Dein OpenRouter-Guthaben ist leer. Bitte lade auf openrouter.ai Guthaben auf."
                403 -> "Zugriff verweigert (z. B. durch die Moderation des Modells oder Key-Limits)."
                404 -> "Modell nicht gefunden. Bitte prüfe den Modellnamen in den Einstellungen."
                408 -> "Zeitüberschreitung bei OpenRouter. Bitte erneut versuchen."
                429 -> "Zu viele Anfragen. Bitte kurz warten und erneut versuchen."
                502, 503 -> "Das Modell ist gerade nicht erreichbar. Bitte später erneut versuchen oder ein anderes Modell wählen."
                in 500..599 -> "Serverfehler bei OpenRouter ($code). Bitte später erneut versuchen."
                else -> "Fehler bei der Anfrage (Code $code)."
            }
            return DealCheckException(text + detail)
        }
    }
}
