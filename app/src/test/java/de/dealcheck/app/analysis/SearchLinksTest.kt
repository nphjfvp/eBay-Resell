package de.dealcheck.app.analysis

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchLinksTest {
    @Test
    fun ebaySoldUrl() {
        assertEquals(
            "https://www.ebay.de/sch/i.html?_nkw=iPhone+12+64GB&LH_Sold=1&LH_Complete=1",
            SearchLinks.ebaySold(" iPhone 12 64GB "),
        )
    }

    @Test
    fun kleinanzeigenUrl() {
        assertEquals("https://www.kleinanzeigen.de/s-iphone-12-64gb/k0", SearchLinks.kleinanzeigen("iPhone  12 64GB"))
        assertEquals("https://www.kleinanzeigen.de/s-m%C3%A4hroboter/k0", SearchLinks.kleinanzeigen("Mähroboter"))
    }
}
