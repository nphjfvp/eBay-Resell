package de.dealcheck.app.analysis

import java.net.URLEncoder

object SearchLinks {
    fun ebaySold(term: String): String =
        "https://www.ebay.de/sch/i.html?_nkw=${URLEncoder.encode(term.trim(), "UTF-8")}&LH_Sold=1&LH_Complete=1"

    fun kleinanzeigen(term: String): String {
        val slug = term.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .joinToString("-") { URLEncoder.encode(it.lowercase(), "UTF-8") }
        return "https://www.kleinanzeigen.de/s-$slug/k0"
    }
}
