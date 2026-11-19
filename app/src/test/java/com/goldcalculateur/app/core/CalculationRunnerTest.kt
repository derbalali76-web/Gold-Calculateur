package com.goldcalculateur.app.core

import com.goldcalculateur.engine.CalcError
import com.goldcalculateur.engine.ScalePrecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode

class CalculationRunnerTest {

    private fun work(mode: CalcMode, target: String, ka: String = "1000", vararg rows: Pair<String, String>) =
        WorkState(rows.mapIndexed { i, (w, k) -> IngotRow(i.toLong(), w, k) }, target, mode, ka)

    // The brief's printed figures correspond to 40 g @ 742 (see engine tests).
    private val sheet = arrayOf("100" to "700", "60" to "720", "40" to "742")

    private fun BigDecimal.r(n: Int = 10) = setScale(n, RoundingMode.HALF_UP).toPlainString()

    @Test fun addPureGoldFromRawInput() {
        val r = CalculationRunner.run(work(CalcMode.ADD_INGOT, "748", "1000", *sheet), ScalePrecision.G_0_001, null)
        assertTrue(r.isSuccess)
        r as ResultUi.Added
        assertEquals("26.6666666667", r.result.theoreticalWeight.r())
        assertEquals("26.667", r.result.practicalWeight.toPlainString())
    }

    @Test fun addWithFrenchDecimalComma() {
        val r = CalculationRunner.run(
            work(CalcMode.ADD_INGOT, "748", "1000", "100" to "700", "60,5" to "720", "40" to "742"),
            ScalePrecision.G_0_01, null,
        )
        assertTrue(r.isSuccess)
    }

    @Test fun cutDefaultsToTheBestIngot() {
        val r = CalculationRunner.run(work(CalcMode.CUT_REFINE, "748", "1000", *sheet), ScalePrecision.G_0_001, null)
        r as ResultUi.Cut
        assertTrue(r.isSuccess)
        assertEquals("29.9465240642", r.analysis.plan!!.theoreticalCut.r())
        assertEquals(listOf(1, 2, 3), r.rowNumbers)
    }

    @Test fun cutSelectionChangesThePlan() {
        val r = CalculationRunner.run(work(CalcMode.CUT_REFINE, "748", "1000", *sheet), ScalePrecision.G_0_001, 2)
        r as ResultUi.Cut
        assertEquals(2, r.analysis.selectedIndex)
        assertEquals("34.8215396095", r.analysis.plan!!.theoreticalCut.r())
    }

    @Test fun blankRowsAreIgnoredAndRowNumbersKept() {
        val w = WorkState(
            listOf(IngotRow(0, "100", "700"), IngotRow(1), IngotRow(2, "50", "500")),
            "750", CalcMode.CUT_REFINE, "1000",
        )
        val r = CalculationRunner.run(w, ScalePrecision.G_0_001, null) as ResultUi.Cut
        assertEquals(listOf(1, 3), r.rowNumbers)
        assertEquals(2, r.analysis.candidates.size)
    }

    @Test fun halfFilledRowIsReportedWithItsPosition() {
        val r = CalculationRunner.run(
            work(CalcMode.ADD_INGOT, "748", "1000", "100" to "700", "60" to ""), ScalePrecision.G_0_001, null,
        ) as ResultUi.Failure
        assertEquals(UiError.RowIncomplete(2), r.error)
        assertEquals(1, r.badRow)
    }

    @Test fun zeroWeightAndBadKaratAreRejected() {
        val a = CalculationRunner.run(work(CalcMode.ADD_INGOT, "748", "1000", "0" to "700"), ScalePrecision.G_0_001, null)
        assertEquals(UiError.Engine(CalcError.INVALID_WEIGHT), (a as ResultUi.Failure).error)
        val b = CalculationRunner.run(work(CalcMode.ADD_INGOT, "748", "1000", "10" to "1001"), ScalePrecision.G_0_001, null)
        assertEquals(UiError.Engine(CalcError.INVALID_KARAT), (b as ResultUi.Failure).error)
    }

    @Test fun missingInputs() {
        val none = CalculationRunner.run(work(CalcMode.ADD_INGOT, "748", "1000"), ScalePrecision.G_0_001, null)
        assertEquals(UiError.Engine(CalcError.NO_INGOTS), (none as ResultUi.Failure).error)
        val t = CalculationRunner.run(work(CalcMode.ADD_INGOT, "", "1000", "10" to "500"), ScalePrecision.G_0_001, null)
        assertEquals(UiError.TargetMissing, (t as ResultUi.Failure).error)
        val k = CalculationRunner.run(work(CalcMode.ADD_INGOT, "700", "", "10" to "500"), ScalePrecision.G_0_001, null)
        assertEquals(UiError.AddedKaratMissing, (k as ResultUi.Failure).error)
    }

    @Test fun impossibleAdjustmentsAreFailuresNotInventedResults() {
        val same = CalculationRunner.run(work(CalcMode.ADD_INGOT, "800", "800", "10" to "500"), ScalePrecision.G_0_001, null)
        assertEquals(UiError.Engine(CalcError.NO_SOLUTION_SAME_KARAT), (same as ResultUi.Failure).error)
        assertFalse(same.isSuccess)
        val cutDown = CalculationRunner.run(work(CalcMode.CUT_REFINE, "400", "1000", "10" to "500"), ScalePrecision.G_0_001, null)
        assertEquals(UiError.Engine(CalcError.TARGET_BELOW_CURRENT), (cutDown as ResultUi.Failure).error)
    }

    @Test fun liveSummaryUsesTheWeightedAverage() {
        val s = CalculationRunner.liveSummary(work(CalcMode.ADD_INGOT, "", "1000", *sheet))!!
        assertEquals("714.4", s.averageKarat.setScale(1, RoundingMode.HALF_UP).toPlainString())
        assertEquals("200", s.totalWeight.toPlainString())
    }

    @Test fun liveSummaryIgnoresIncompleteRowsAndIsNullWhenEmpty() {
        assertNull(CalculationRunner.liveSummary(WorkState.DEFAULT))
        val s = CalculationRunner.liveSummary(work(CalcMode.ADD_INGOT, "", "1000", "100" to "750", "50" to ""))!!
        assertEquals("100", s.totalWeight.toPlainString())
    }

    @Test fun scaleSettingChangesOnlyThePracticalWeight() {
        val w = work(CalcMode.ADD_INGOT, "748", "1000", *sheet)
        val a = CalculationRunner.run(w, ScalePrecision.G_0_01, null) as ResultUi.Added
        val b = CalculationRunner.run(w, ScalePrecision.G_0_0001, null) as ResultUi.Added
        assertEquals("26.67", a.result.practicalWeight.toPlainString())
        assertEquals("26.6667", b.result.practicalWeight.toPlainString())
        assertEquals(0, a.result.theoreticalWeight.compareTo(b.result.theoreticalWeight))
    }
}
