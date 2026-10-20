package de.dealcheck.app.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfitCalcTest {
    @Test
    fun computesProfitAndMargin() {
        val p = ProfitCalc.compute(purchase = 100.0, shipping = 5.0, parts = 45.0, sale = 250.0)!!
        assertEquals(150.0, p.costs, 0.001)
        assertEquals(100.0, p.profit, 0.001)
        assertEquals(40.0, p.marginPercent!!, 0.001)
    }

    @Test
    fun missingCostsCountAsZero() {
        assertEquals(200.0, ProfitCalc.compute(null, null, null, 200.0)!!.profit, 0.001)
    }

    @Test
    fun noSalePriceNoResult() {
        assertNull(ProfitCalc.compute(100.0, 5.0, 10.0, null))
    }

    @Test
    fun parsesInput() {
        assertEquals(12.5, ProfitCalc.parseInput("12,5")!!, 0.001)
        assertEquals(12.5, ProfitCalc.parseInput("12.50 €")!!, 0.001)
        assertNull(ProfitCalc.parseInput(""))
    }

    @Test
    fun midpoint() {
        assertEquals(75.0, ProfitCalc.midpoint(50.0, 100.0)!!, 0.001)
        assertEquals(50.0, ProfitCalc.midpoint(50.0, null)!!, 0.001)
        assertNull(ProfitCalc.midpoint(null, null))
    }
}
