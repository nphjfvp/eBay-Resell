package de.dealcheck.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val apiKey: String = "",
    val model: String = SettingsRepository.DEFAULT_MODEL,
    val webSearch: Boolean = false,
)

/**
 * Speichert die Einstellungen in den privaten SharedPreferences der App.
 * Diese liegen nur auf dem Gerät (Backup ist im Manifest deaktiviert).
 */
class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun load() = AppSettings(
        apiKey = prefs.getString(KEY_API_KEY, "").orEmpty(),
        model = prefs.getString(KEY_MODEL, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_MODEL,
        webSearch = prefs.getBoolean(KEY_WEB_SEARCH, false),
    )

    fun update(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_API_KEY, settings.apiKey.trim())
            .putString(KEY_MODEL, settings.model.trim().ifBlank { DEFAULT_MODEL })
            .putBoolean(KEY_WEB_SEARCH, settings.webSearch)
            .apply()
        _settings.value = load()
    }

    companion object {
        const val DEFAULT_MODEL = "google/gemini-2.5-flash"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_WEB_SEARCH = "web_search"

        /** Zeigt nur Anfang und Ende des Keys, z. B. "sk-or-v1-…a1b2". */
        fun mask(key: String): String = when {
            key.isBlank() -> "nicht eingetragen"
            key.length <= 12 -> "•".repeat(key.length)
            else -> key.take(8) + "…" + key.takeLast(4)
        }
    }
}
