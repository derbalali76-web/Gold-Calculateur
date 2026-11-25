package com.goldcalculateur.app.core

import com.goldcalculateur.engine.ScalePrecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** Extra safety nets: billing decisions and end-to-end calculations at the app's 2-decimal rounding. */
class BillingDecisionEdgeCasesTest {
    private val id = "remove_ads_lifetime"
    private fun p(state: PurchaseStateKind, ack: Boolean, token: String, products: List<String> = listOf("remove_ads_lifetime")) =
        PurchaseSnapshot(products, state, ack, token)

    @Test fun unspecifiedStateIsNeverAnEntitlement() {
        val d = EntitlementLogic.decide(id, listOf(p(PurchaseStateKind.UNSPECIFIED, false, "x")))
        assertFalse(d.entitled)
        assertFalse(d.pending)
        assertTrue(d.tokensToAcknowledge.isEmpty())
    }

    @Test fun purchasedWinsOverAStalePendingEntry() {
        val d = EntitlementLogic.decide(
            id, listOf(p(PurchaseStateKind.PENDING, false, "a"), p(PurchaseStateKind.PURCHASED, false, "b")),
        )
        assertTrue(d.entitled)
        assertEquals(listOf("b"), d.tokensToAcknowledge) // a pending token is never acknowledged
    }

    @Test fun purchaseOfOurProductInsideAMultiProductPurchaseCounts() {
        val d = EntitlementLogic.decide(id, listOf(p(PurchaseStateKind.PURCHASED, true, "t", listOf("other", id))))
        assertTrue(d.entitled)
    }

    @Test fun purchaseOfAnotherProductNeverUnlocks() {
        val d = EntitlementLogic.decide(id, listOf(p(PurchaseStateKind.PURCHASED, false, "t", listOf("other"))))
        assertFalse(d.entitled)
        assertTrue(d.tokensToAcknowledge.isEmpty())
    }
}

class TwoDecimalCalculationTest {
    private fun work(mode: CalcMode, target: String, added: String, vararg rows: Pair<String, String>) =
        WorkState(rows.mapIndexed { i, (w, k) -> IngotRow(i.toLong(), w, k) }, target, mode, added)

    private val rows = arrayOf("100" to "700", "60" to "720", "40" to "750")

    @Test fun addPureGoldRoundsToTwoDecimals() {
        val r = CalculationRunner.run(work(CalcMode.ADD_INGOT, "748", "1000", *rows), ScalePrecision.G_0_01, null)
        r as ResultUi.Added
        assertEquals("25.40", r.result.practicalWeight.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString())
    }

    @Test fun addLowerKaratToRaiseTheGroup() {
        val r = CalculationRunner.run(work(CalcMode.ADD_INGOT, "748", "875", *rows), ScalePrecision.G_0_01, null)
        r as ResultUi.Added
        assertTrue(r.isSuccess)
        // (748*200 - 143200) / (875 - 748) = 6400 / 127 = 50.39...
        assertEquals("50.39", r.result.practicalWeight.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString())
    }

    @Test fun lowerKaratByAddingBaseMetal() {
        val r = CalculationRunner.run(work(CalcMode.ADD_INGOT, "600", "0", *rows), ScalePrecision.G_0_01, null)
        r as ResultUi.Added
        assertTrue(r.isSuccess)
        // 143200 = 600 * (200 + w) -> w = 38.666...
        assertEquals("38.67", r.result.practicalWeight.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString())
    }

    @Test fun cutDefaultsAndFinalKaratIsNearTarget() {
        val r = CalculationRunner.run(work(CalcMode.CUT_REFINE, "748", "1000", *rows), ScalePrecision.G_0_01, null)
        r as ResultUi.Cut
        assertTrue(r.isSuccess)
        val plan = r.analysis.plan!!
        assertEquals(2, plan.practicalCut.scale())
        assertTrue(plan.practicalFinalKarat.subtract(BigDecimal("748")).abs() < BigDecimal("0.5"))
    }

    @Test fun inputsNeverCrashOrDivideByZero() {
        val odd = listOf(
            work(CalcMode.ADD_INGOT, "1000", "1000", *rows),      // added karat == target
            work(CalcMode.ADD_INGOT, "0", "0", "10" to "0"),      // all zero karat
            work(CalcMode.CUT_REFINE, "1000", "1000", "10" to "1000"),
            work(CalcMode.CUT_REFINE, "748", "1000", "0.01" to "700"),
            work(CalcMode.ADD_INGOT, "748", "1000", "9999999" to "1"),
        )
        odd.forEach { w ->
            val r = CalculationRunner.run(w, ScalePrecision.G_0_01, null) // must return, never throw
            assertTrue(r.isSuccess || r is ResultUi.Failure || r is ResultUi.Cut)
        }
    }

    @Test fun invalidInputsAreReportedNotThrown() {
        val r = CalculationRunner.run(work(CalcMode.ADD_INGOT, "abc", "1000", *rows), ScalePrecision.G_0_01, null)
        assertFalse(r.isSuccess)
        assertTrue(r is ResultUi.Failure)
    }
}
