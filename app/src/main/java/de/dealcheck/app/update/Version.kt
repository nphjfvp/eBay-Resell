package de.dealcheck.app.update

/** Einfacher SemVer-Vergleich: "v1.2.0" > "1.2.0-dev.5" > "1.1.9". */
object Version {
    private data class Parsed(val core: List<Int>, val preRelease: Boolean)

    private fun parse(v: String): Parsed {
        val clean = v.trim().removePrefix("v").removePrefix("V")
        val core = clean.substringBefore('-').substringBefore('+')
        val parts = core.split('.').map { it.toIntOrNull() ?: 0 }
        return Parsed(parts + List((3 - parts.size).coerceAtLeast(0)) { 0 }, clean.contains('-'))
    }

    /** true, wenn [remote] neuer ist als [installed]. */
    fun isNewer(remote: String, installed: String): Boolean {
        val r = parse(remote)
        val i = parse(installed)
        for (k in 0 until maxOf(r.core.size, i.core.size)) {
            val a = r.core.getOrElse(k) { 0 }
            val b = i.core.getOrElse(k) { 0 }
            if (a != b) return a > b
        }
        // gleiche Versionsnummer: finales Release ist neuer als ein Entwicklungs-Build
        return i.preRelease && !r.preRelease
    }
}
