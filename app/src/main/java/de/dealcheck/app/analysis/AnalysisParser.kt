package de.dealcheck.app.analysis

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class AnalysisParseException(message: String) : Exception(message)

/** Liest die Modellantwort robust ein: Codeblöcke und Text um das JSON herum werden ignoriert. */
object AnalysisParser {

    fun extractJson(raw: String): String {
        var text = raw.trim()
        // ```json ... ``` entfernen
        val fence = Regex("```(?:json|JSON)?\\s*([\\s\\S]*?)```").find(text)
        if (fence != null) text = fence.groupValues[1].trim()
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) throw AnalysisParseException("Antwort enthält kein JSON-Objekt")
        return text.substring(start, end + 1)
    }

    /** [strict] = false beim Laden aus dem Verlauf, dort wird nicht mehr nachgefragt. */
    fun parse(raw: String, strict: Boolean = true): DealAnalysis {
        val obj = try {
            JSONObject(extractJson(raw))
        } catch (e: JSONException) {
            throw AnalysisParseException("Ungültiges JSON: ${e.message}")
        }
        val analysis = DealAnalysis(
            geraet = obj.str("geraet"),
            modell = obj.str("modell"),
            defektLautAnzeige = obj.str("defekt_laut_anzeige"),
            angebotspreisEur = obj.num("angebotspreis_eur"),
            versandEur = obj.num("versand_eur"),
            verkaeuferHinweise = obj.str("verkaeufer_hinweise"),
            vermuteteUrsachen = obj.list("vermutete_ursachen"),
            reparaturKostenMinEur = obj.num("reparatur_kosten_min_eur"),
            reparaturKostenMaxEur = obj.num("reparatur_kosten_max_eur"),
            verkaufspreisRepariertMinEur = obj.num("verkaufspreis_repariert_min_eur"),
            verkaufspreisRepariertMaxEur = obj.num("verkaufspreis_repariert_max_eur"),
            gewinnMinEur = obj.num("gewinn_min_eur"),
            gewinnMaxEur = obj.num("gewinn_max_eur"),
            schwierigkeit = obj.num("schwierigkeit_1_bis_5")?.toInt()?.coerceIn(1, 5),
            risiken = obj.list("risiken"),
            warnsignaleBetrug = obj.list("warnsignale_betrug"),
            empfehlung = Recommendation.parse(obj.str("empfehlung")),
            maxKaufpreisEur = obj.num("max_kaufpreis_eur"),
            begruendung = obj.str("begruendung"),
            suchbegriffEbay = obj.str("suchbegriff_ebay"),
        )
        if (strict && analysis.empfehlung == Recommendation.UNBEKANNT && analysis.geraet == null) {
            throw AnalysisParseException("JSON enthält nicht die erwarteten Felder")
        }
        return analysis
    }

    private fun JSONObject.str(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return when (val v = get(key)) {
            is String -> v.trim().takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
            is JSONArray -> (0 until v.length()).joinToString(", ") { v.get(it).toString() }
            else -> v.toString()
        }
    }

    private fun JSONObject.num(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return when (val v = get(key)) {
            is Number -> v.toDouble()
            is String -> parseNumber(v)
            else -> null
        }
    }

    private fun JSONObject.list(key: String): List<String> {
        if (!has(key) || isNull(key)) return emptyList()
        return when (val v = get(key)) {
            is JSONArray -> (0 until v.length())
                .mapNotNull { i -> if (v.isNull(i)) null else v.get(i).toString().trim() }
                .filter { it.isNotEmpty() }
            is String -> listOf(v.trim()).filter { it.isNotEmpty() }
            else -> listOf(v.toString())
        }
    }

    /** "ca. 1.234,50 €" -> 1234.5, "50-80" -> 50.0 */
    internal fun parseNumber(text: String): Double? {
        val match = Regex("-?\\d[\\d.,]*").find(text) ?: return null
        var n = match.value.trimEnd('.', ',')
        val lastComma = n.lastIndexOf(',')
        val lastDot = n.lastIndexOf('.')
        n = when {
            lastComma >= 0 && lastDot >= 0 ->
                if (lastComma > lastDot) n.replace(".", "").replace(',', '.') else n.replace(",", "")
            lastComma >= 0 ->
                if (n.length - lastComma - 1 == 3) n.replace(",", "") else n.replace(',', '.')
            lastDot >= 0 && n.count { it == '.' } > 1 -> n.replace(".", "")
            lastDot >= 0 && n.length - lastDot - 1 == 3 -> n.replace(".", "")
            else -> n
        }
        return n.toDoubleOrNull()
    }

    /** Wandelt eine Analyse wieder in JSON um (für die Speicherung im Verlauf). */
    fun toJson(a: DealAnalysis): String = JSONObject().apply {
        put("geraet", a.geraet ?: JSONObject.NULL)
        put("modell", a.modell ?: JSONObject.NULL)
        put("defekt_laut_anzeige", a.defektLautAnzeige ?: JSONObject.NULL)
        put("angebotspreis_eur", a.angebotspreisEur ?: JSONObject.NULL)
        put("versand_eur", a.versandEur ?: JSONObject.NULL)
        put("verkaeufer_hinweise", a.verkaeuferHinweise ?: JSONObject.NULL)
        put("vermutete_ursachen", JSONArray(a.vermuteteUrsachen))
        put("reparatur_kosten_min_eur", a.reparaturKostenMinEur ?: JSONObject.NULL)
        put("reparatur_kosten_max_eur", a.reparaturKostenMaxEur ?: JSONObject.NULL)
        put("verkaufspreis_repariert_min_eur", a.verkaufspreisRepariertMinEur ?: JSONObject.NULL)
        put("verkaufspreis_repariert_max_eur", a.verkaufspreisRepariertMaxEur ?: JSONObject.NULL)
        put("gewinn_min_eur", a.gewinnMinEur ?: JSONObject.NULL)
        put("gewinn_max_eur", a.gewinnMaxEur ?: JSONObject.NULL)
        put("schwierigkeit_1_bis_5", a.schwierigkeit ?: JSONObject.NULL)
        put("risiken", JSONArray(a.risiken))
        put("warnsignale_betrug", JSONArray(a.warnsignaleBetrug))
        put("empfehlung", a.empfehlung.label)
        put("max_kaufpreis_eur", a.maxKaufpreisEur ?: JSONObject.NULL)
        put("begruendung", a.begruendung ?: JSONObject.NULL)
        put("suchbegriff_ebay", a.suchbegriffEbay ?: JSONObject.NULL)
    }.toString()
}
