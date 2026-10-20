package de.dealcheck.app.ui

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val germany = Locale.GERMANY

fun formatEur(value: Double?): String {
    if (value == null) return "–"
    val nf = NumberFormat.getNumberInstance(germany).apply {
        val whole = value % 1.0 == 0.0
        minimumFractionDigits = if (whole) 0 else 2
        maximumFractionDigits = if (whole) 0 else 2
    }
    return nf.format(value) + " €"
}

fun formatRange(min: Double?, max: Double?): String = when {
    min == null && max == null -> "–"
    min == null || max == null || min == max -> formatEur(min ?: max)
    else -> "${formatEur(min).removeSuffix(" €")} – ${formatEur(max)}"
}

/** Für Eingabefelder: "12,5" statt "12,50 €". */
fun formatInput(value: Double?): String {
    if (value == null) return ""
    val nf = NumberFormat.getNumberInstance(germany).apply {
        isGroupingUsed = false
        maximumFractionDigits = 2
    }
    return nf.format(value)
}

fun formatDate(millis: Long): String =
    SimpleDateFormat("dd.MM.yyyy, HH:mm", germany).format(Date(millis))
