package de.dealcheck.app.analysis

enum class Recommendation(val label: String) {
    KAUFEN("KAUFEN"),
    VERHANDELN("VERHANDELN"),
    NICHT_KAUFEN("NICHT KAUFEN"),
    UNBEKANNT("UNKLAR");

    companion object {
        fun parse(value: String?): Recommendation {
            val v = value.orEmpty().uppercase().replace('_', ' ').trim()
            return when {
                v.startsWith("NICHT") -> NICHT_KAUFEN
                v.startsWith("VERHANDELN") -> VERHANDELN
                v.startsWith("KAUFEN") -> KAUFEN
                else -> UNBEKANNT
            }
        }
    }
}

/** Ergebnis einer Angebotsanalyse, entspricht dem JSON-Schema aus dem Systemprompt. */
data class DealAnalysis(
    val geraet: String?,
    val modell: String?,
    val defektLautAnzeige: String?,
    val angebotspreisEur: Double?,
    val versandEur: Double?,
    val verkaeuferHinweise: String?,
    val vermuteteUrsachen: List<String>,
    val reparaturKostenMinEur: Double?,
    val reparaturKostenMaxEur: Double?,
    val verkaufspreisRepariertMinEur: Double?,
    val verkaufspreisRepariertMaxEur: Double?,
    val gewinnMinEur: Double?,
    val gewinnMaxEur: Double?,
    val schwierigkeit: Int?,
    val risiken: List<String>,
    val warnsignaleBetrug: List<String>,
    val empfehlung: Recommendation,
    val maxKaufpreisEur: Double?,
    val begruendung: String?,
    val suchbegriffEbay: String?,
) {
    val title: String
        get() = listOfNotNull(geraet, modell)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")
            .ifBlank { "Unbekanntes Gerät" }

    /** Suchbegriff für eBay/Kleinanzeigen, notfalls aus Gerät + Modell gebildet. */
    val searchTerm: String
        get() = suchbegriffEbay?.takeIf { it.isNotBlank() } ?: title
}
