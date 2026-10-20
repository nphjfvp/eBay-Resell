package de.dealcheck.app.analysis

data class Profit(val costs: Double, val profit: Double, val marginPercent: Double?)

object ProfitCalc {
    /** Gewinn = Verkaufspreis − (Einkauf + Versand + Teile); Marge bezogen auf den Verkaufspreis. */
    fun compute(purchase: Double?, shipping: Double?, parts: Double?, sale: Double?): Profit? {
        if (sale == null) return null
        val costs = (purchase ?: 0.0) + (shipping ?: 0.0) + (parts ?: 0.0)
        val profit = sale - costs
        val margin = if (sale > 0) profit / sale * 100 else null
        return Profit(costs, profit, margin)
    }

    /** Akzeptiert "12,50", "12.50", "12 €" und Leerstring (= null). */
    fun parseInput(text: String): Double? =
        text.replace("€", "").replace(" ", "").replace(',', '.').toDoubleOrNull()

    fun midpoint(min: Double?, max: Double?): Double? = when {
        min != null && max != null -> (min + max) / 2
        else -> min ?: max
    }
}
