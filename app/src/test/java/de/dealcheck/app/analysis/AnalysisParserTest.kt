package de.dealcheck.app.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisParserTest {

    private val sample = """
        {
          "geraet": "Smartphone",
          "modell": "iPhone 12",
          "defekt_laut_anzeige": "Display gebrochen",
          "angebotspreis_eur": 120,
          "versand_eur": "5,99 €",
          "verkaeufer_hinweise": null,
          "vermutete_ursachen": ["Sturzschaden"],
          "reparatur_kosten_min_eur": 40,
          "reparatur_kosten_max_eur": 90.5,
          "verkaufspreis_repariert_min_eur": 250,
          "verkaufspreis_repariert_max_eur": 300,
          "gewinn_min_eur": 30,
          "gewinn_max_eur": 130,
          "schwierigkeit_1_bis_5": 2,
          "risiken": ["Face ID defekt?"],
          "warnsignale_betrug": [],
          "empfehlung": "VERHANDELN",
          "max_kaufpreis_eur": 100,
          "begruendung": "Lohnt sich nur günstiger.",
          "suchbegriff_ebay": "iPhone 12 64GB"
        }
    """.trimIndent()

    @Test
    fun parsesPlainJson() {
        val a = AnalysisParser.parse(sample)
        assertEquals("iPhone 12", a.modell)
        assertEquals(120.0, a.angebotspreisEur!!, 0.001)
        assertEquals(5.99, a.versandEur!!, 0.001)
        assertNull(a.verkaeuferHinweise)
        assertEquals(Recommendation.VERHANDELN, a.empfehlung)
        assertEquals(2, a.schwierigkeit)
        assertTrue(a.warnsignaleBetrug.isEmpty())
        assertEquals("iPhone 12 64GB", a.searchTerm)
    }

    @Test
    fun stripsCodeFenceAndSurroundingText() {
        val raw = "Hier ist die Analyse:\n```json\n$sample\n```\nViel Erfolg!"
        assertEquals(Recommendation.VERHANDELN, AnalysisParser.parse(raw).empfehlung)
    }

    @Test
    fun parsesRecommendationVariants() {
        assertEquals(Recommendation.NICHT_KAUFEN, Recommendation.parse("nicht kaufen"))
        assertEquals(Recommendation.NICHT_KAUFEN, Recommendation.parse("NICHT_KAUFEN"))
        assertEquals(Recommendation.KAUFEN, Recommendation.parse("Kaufen"))
        assertEquals(Recommendation.UNBEKANNT, Recommendation.parse(null))
    }

    @Test(expected = AnalysisParseException::class)
    fun rejectsNonJson() {
        AnalysisParser.parse("Ich kann das Bild leider nicht erkennen.")
    }

    @Test(expected = AnalysisParseException::class)
    fun rejectsBrokenJson() {
        AnalysisParser.parse("{\"geraet\": \"Laptop\", ")
    }

    @Test
    fun roundTripsThroughJson() {
        val a = AnalysisParser.parse(sample)
        assertEquals(a, AnalysisParser.parse(AnalysisParser.toJson(a)))
    }

    @Test
    fun parsesGermanNumbers() {
        assertEquals(1234.5, AnalysisParser.parseNumber("ca. 1.234,50 €")!!, 0.001)
        assertEquals(50.0, AnalysisParser.parseNumber("50-80")!!, 0.001)
        assertEquals(19.99, AnalysisParser.parseNumber("19.99")!!, 0.001)
        assertEquals(1500.0, AnalysisParser.parseNumber("1.500 EUR")!!, 0.001)
        assertNull(AnalysisParser.parseNumber("unbekannt"))
    }
}
