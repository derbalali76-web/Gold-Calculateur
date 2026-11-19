package com.goldcalculateur.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Random

class GoldEngineTest {

    private fun bd(s: String) = BigDecimal(s)
    private fun ing(w: String, k: String) = Ingot(bd(w), bd(k))
    /**
     * Dataset that reproduces EVERY figure printed in the project brief (714.4, 142.88, 26.6667, 44.2105,
     * 29.9465...). NOTE: the brief lists the third ingot as 40 g @ 750, but with 750 the group is
     * 143.2 g gold / karat 716, not 142.88 / 714.4. The printed figures correspond to 40 g @ 742.
     */
    private val spec = listOf(ing("100", "700"), ing("60", "720"), ing("40", "742"))

    /** The rows exactly as written in the brief (100@700, 60@720, 40@750). */
    private val literal = listOf(ing("100", "700"), ing("60", "720"), ing("40", "750"))
    private val eps = bd("1e-30")

    /** Value rounded half-up to [scale] decimals, as plain text. */
    private fun r(v: BigDecimal, scale: Int = 10) = v.setScale(scale, RoundingMode.HALF_UP).toPlainString()

    private fun assertNear(expected: String, actual: BigDecimal, tol: String = "1e-12") {
        val diff = bd(expected).subtract(actual).abs()
        assertTrue("expected $expected but was ${actual.toPlainString()}", diff <= bd(tol))
    }

    private fun <T> success(o: Outcome<T>): T = when (o) {
        is Outcome.Success -> o.value
        is Outcome.Failure -> throw AssertionError("expected success but got ${o.error}")
    }

    private fun assertFailure(expected: CalcError, o: Outcome<*>) {
        when (o) {
            is Outcome.Success -> fail("expected $expected but got success: ${o.value}")
            is Outcome.Failure -> assertEquals(expected, o.error)
        }
    }

    // ------------------------------------------------------------ summary

    @Test fun singleIngotSummary() {
        val s = success(GoldEngine.summarize(listOf(ing("100", "750"))))
        assertEquals("100", s.totalWeight.toPlainString())
        assertNear("75", s.pureGold)
        assertNear("750", s.averageKarat)
    }

    @Test fun weightedAverageIsNotTheSimpleAverage() {
        val s = success(GoldEngine.summarize(spec))
        assertEquals("200", s.totalWeight.toPlainString())
        assertNear("142.88", s.pureGold)
        assertNear("714.4", s.averageKarat)
        // simple average would be 723.333...
        assertTrue(s.averageKarat.subtract(bd("723.3333")).abs() > bd("1"))
    }

    @Test fun decimalWeightsAndKaratsAreExact() {
        val s = success(GoldEngine.summarize(listOf(ing("33.3333", "916.67"), ing("0.0001", "1"))))
        // 33.3333*916.67 = 30555.5...
        assertEquals(bd("33.3333").multiply(bd("916.67")).add(bd("0.0001")), s.goldProduct)
        assertEquals(bd("33.3334"), s.totalWeight)
    }

    @Test fun fiveIngotsGroup() {
        val l = listOf(ing("10", "999"), ing("20", "916"), ing("30", "875"), ing("40", "750"), ing("50", "585"))
        val s = success(GoldEngine.summarize(l))
        assertEquals("150", s.totalWeight.toPlainString())
        // P = 9990+18320+26250+30000+29250 = 113810  -> Kc = 758.7333...
        assertEquals("113810", s.goldProduct.toPlainString())
        assertEquals("758.7333333333", r(s.averageKarat))
    }

    // ------------------------------------------------------------ the brief's rows taken literally

    @Test fun literalRowsFromTheBriefGiveKarat716NotTheBrief714_4() {
        val s = success(GoldEngine.summarize(literal))
        assertEquals("143200", s.goldProduct.toPlainString())
        assertNear("143.2", s.pureGold)
        assertNear("716", s.averageKarat)
    }

    @Test fun literalRowsAddAndCutAreSelfConsistent() {
        val a = success(GoldEngine.addIngot(literal, bd("748"), bd("1000")))
        assertEquals("25.3968253968", r(a.theoreticalWeight))
        assertEquals("225.3968253968", r(a.theoreticalFinalWeight))
        assertNear("748", a.theoreticalFinalKarat, "1e-30")
        val a9 = success(GoldEngine.addIngot(literal, bd("748"), bd("900")))
        assertEquals("42.1052631579", r(a9.theoreticalWeight))
        val c = success(GoldEngine.cutAndRefine(literal, bd("748"), selectedIndex = 0)).plan!!
        assertEquals("28.5204991087", r(c.theoreticalCut))
        assertEquals("191.4438502674", r(c.theoreticalFinalWeight))
        assertNear("748", c.theoreticalFinalKarat, "1e-30")
    }

    // ------------------------------------------------------------ validation

    @Test fun emptyListIsRejected() = assertFailure(CalcError.NO_INGOTS, GoldEngine.summarize(emptyList()))

    @Test fun zeroWeightIsRejected() =
        assertFailure(CalcError.INVALID_WEIGHT, GoldEngine.summarize(listOf(ing("0", "750"))))

    @Test fun negativeWeightIsRejected() =
        assertFailure(CalcError.INVALID_WEIGHT, GoldEngine.summarize(listOf(ing("-1", "750"))))

    @Test fun karatAbove1000IsRejected() =
        assertFailure(CalcError.INVALID_KARAT, GoldEngine.summarize(listOf(ing("10", "1001"))))

    @Test fun negativeKaratIsRejected() =
        assertFailure(CalcError.INVALID_KARAT, GoldEngine.summarize(listOf(ing("10", "-1"))))

    @Test fun zeroKaratIsValid() {
        val s = success(GoldEngine.summarize(listOf(ing("10", "0"))))
        assertEquals(0, s.averageKarat.signum())
    }

    @Test fun invalidTargetsAreRejected() {
        assertFailure(CalcError.INVALID_TARGET, GoldEngine.addIngot(spec, bd("0"), bd("1000")))
        assertFailure(CalcError.INVALID_TARGET, GoldEngine.addIngot(spec, bd("1000.5"), bd("1000")))
        assertFailure(CalcError.INVALID_TARGET, GoldEngine.cutAndRefine(spec, bd("-5")))
        assertFailure(CalcError.INVALID_TARGET, GoldEngine.cutAndRefine(spec, bd("1001")))
    }

    @Test fun invalidAddedKaratIsRejected() {
        assertFailure(CalcError.INVALID_ADDED_KARAT, GoldEngine.addIngot(spec, bd("748"), bd("1001")))
        assertFailure(CalcError.INVALID_ADDED_KARAT, GoldEngine.addIngot(spec, bd("748"), bd("-1")))
    }

    // ------------------------------------------------------------ add ingot (mandatory examples)

    @Test fun spec_add_pure_gold_1000() {
        val a = success(GoldEngine.addIngot(spec, bd("748"), bd("1000")))
        assertEquals("26.6666666667", r(a.theoreticalWeight))
        assertEquals("226.6666666667", r(a.theoreticalFinalWeight))
        assertNear("748", a.theoreticalFinalKarat, "1e-30")
    }

    @Test fun spec_add_900() {
        val a = success(GoldEngine.addIngot(spec, bd("748"), bd("900")))
        assertEquals("44.2105263158", r(a.theoreticalWeight))
        assertEquals("244.2105263158", r(a.theoreticalFinalWeight))
        assertNear("748", a.theoreticalFinalKarat, "1e-30")
    }

    @Test fun raiseWithZeroKaratGroup() {
        val a = success(GoldEngine.addIngot(listOf(ing("10", "0")), bd("500"), bd("1000")))
        assertEquals("10", r(a.theoreticalWeight, 0))
        assertEquals("20", r(a.theoreticalFinalWeight, 0))
        assertNear("500", a.theoreticalFinalKarat, "1e-30")
    }

    // ------------------------------------------------------------ lowering karat

    @Test fun lowerKaratByAddingLowerIngot() {
        val a = success(GoldEngine.addIngot(listOf(ing("100", "900")), bd("800"), bd("700")))
        assertEquals("100.0000000000", r(a.theoreticalWeight))
        assertEquals("200.0000000000", r(a.theoreticalFinalWeight))
        assertNear("800", a.theoreticalFinalKarat, "1e-30")
    }

    @Test fun lowerKaratByAddingZeroKaratMetal() {
        // 100 g @ 900 -> 750 using a 0-karat addition: Wa = (75000-90000)/(0-750) = 20
        val a = success(GoldEngine.addIngot(listOf(ing("100", "900")), bd("750"), bd("0")))
        assertEquals("20.0000000000", r(a.theoreticalWeight))
        assertNear("750", a.theoreticalFinalKarat, "1e-30")
    }

    @Test fun wrongDirectionIsRejected() {
        // want to raise (748 > 714.4) but added karat is lower than target
        assertFailure(CalcError.ADDED_KARAT_WRONG_DIRECTION, GoldEngine.addIngot(spec, bd("748"), bd("700")))
        // want to lower (600 < 714.4) but added karat is higher than target
        assertFailure(CalcError.ADDED_KARAT_WRONG_DIRECTION, GoldEngine.addIngot(spec, bd("600"), bd("900")))
    }

    @Test fun targetUnreachableByAdding() {
        // Target 1000 can never be reached by adding an ingot (it would need Ka > 1000).
        assertFailure(CalcError.NO_SOLUTION_SAME_KARAT, GoldEngine.addIngot(spec, bd("1000"), bd("1000")))
        assertFailure(CalcError.ADDED_KARAT_WRONG_DIRECTION, GoldEngine.addIngot(spec, bd("1000"), bd("999")))
    }

    @Test fun addedKaratEqualToTargetHasNoSolution() =
        assertFailure(CalcError.NO_SOLUTION_SAME_KARAT, GoldEngine.addIngot(spec, bd("748"), bd("748")))

    @Test fun targetEqualToCurrentNeedsNoAdjustment() {
        assertFailure(CalcError.TARGET_EQUALS_CURRENT, GoldEngine.addIngot(spec, bd("714.4"), bd("1000")))
        assertFailure(CalcError.TARGET_EQUALS_CURRENT, GoldEngine.cutAndRefine(spec, bd("714.4")))
    }

    @Test fun targetEqualsCurrentForIdenticalIngots() {
        val g = listOf(ing("1", "700"), ing("1", "700"), ing("1", "700"))
        assertFailure(CalcError.TARGET_EQUALS_CURRENT, GoldEngine.addIngot(g, bd("700"), bd("1000")))
    }

    // ------------------------------------------------------------ cut & refine

    @Test fun spec_cut_first_ingot() {
        val a = success(GoldEngine.cutAndRefine(spec, bd("748"), selectedIndex = 0))
        val p = a.plan!!
        assertEquals("29.9465240642", r(p.theoreticalCut))
        // recovered = x * 700/1000
        assertEquals("20.9625668449", r(p.theoreticalRecoveredGold))
        assertEquals("70.0534759358", r(p.theoreticalRemainingWeight))
        // final weight = W - x + recovered = 200 - 29.9465240642 + 20.9625668449
        assertEquals("191.0160427807", r(p.theoreticalFinalWeight))
        assertNear("748", p.theoreticalFinalKarat, "1e-30")
    }

    @Test fun cutAutoSelectsTheSmallestFeasibleCut() {
        val a = success(GoldEngine.cutAndRefine(spec, bd("748")))
        // lowest karat needs the smallest cut -> ingot 0 (700)
        assertEquals(0, a.bestIndex)
        assertEquals(0, a.selectedIndex)
        assertEquals("29.9465240642", r(a.plan!!.theoreticalCut))
    }

    @Test fun cutOtherIngotRecomputes() {
        val a = success(GoldEngine.cutAndRefine(spec, bd("748"), selectedIndex = 2))
        val p = a.plan!!
        // x = 1000*6720/(748*258)
        assertEquals("34.8215396095", r(p.theoreticalCut))
        assertNear("748", p.theoreticalFinalKarat, "1e-30")
        assertEquals("5.1784603905", r(p.theoreticalRemainingWeight))
    }

    @Test fun cutIngotTooSmallIsFlaggedButOthersWork() {
        val l = listOf(ing("5", "800"), ing("95", "500"))
        val a = success(GoldEngine.cutAndRefine(l, bd("900")))
        assertEquals(1, a.bestIndex)
        assertNotNull(a.plan)
        val tooSmall = success(GoldEngine.cutAndRefine(l, bd("900"), selectedIndex = 0))
        assertNull(tooSmall.plan)
        assertEquals(CutStatus.INSUFFICIENT_WEIGHT, tooSmall.selectedStatus)
        assertEquals(1, tooSmall.bestIndex)
    }

    @Test fun cutNoSingleIngotSufficient() {
        val l = listOf(ing("5", "800"), ing("5", "500"))
        assertFailure(CalcError.CUT_NO_SINGLE_INGOT_SUFFICIENT, GoldEngine.cutAndRefine(l, bd("900")))
    }

    @Test fun cutTargetBelowCurrentIsRejected() =
        assertFailure(CalcError.TARGET_BELOW_CURRENT, GoldEngine.cutAndRefine(spec, bd("700")))

    @Test fun pureIngotCannotBeRefined() {
        val l = listOf(ing("50", "1000"), ing("50", "500"))
        val a = success(GoldEngine.cutAndRefine(l, bd("800")))
        assertEquals(CutStatus.PURE_INGOT, a.candidates[0].status)
        assertNull(a.candidates[0].requiredCut)
        assertEquals(1, a.bestIndex)
        assertEquals("12.5000000000", r(a.plan!!.theoreticalCut))
        val pure = success(GoldEngine.cutAndRefine(l, bd("800"), selectedIndex = 0))
        assertNull(pure.plan)
        assertEquals(CutStatus.PURE_INGOT, pure.selectedStatus)
    }

    @Test fun cutTo1000UsesWholeIngotExactly() {
        // 100 g @ 900 -> 1000 means cutting the entire ingot (boundary x == weight is allowed)
        val a = success(GoldEngine.cutAndRefine(listOf(ing("100", "900")), bd("1000")))
        val p = a.plan!!
        assertEquals("100.0000000000", r(p.theoreticalCut))
        assertEquals("90.0000000000", r(p.theoreticalFinalWeight))
        assertNear("1000", p.theoreticalFinalKarat, "1e-30")
        assertEquals(0, p.practicalRemainingWeight.signum())
    }

    @Test fun cutIndexOutOfRange() =
        assertFailure(CalcError.CUT_INDEX_OUT_OF_RANGE, GoldEngine.cutAndRefine(spec, bd("748"), selectedIndex = 9))

    // ------------------------------------------------------------ rounding / balance precision

    @Test fun practicalWeightFollowsScaleButTheoreticalIsKept() {
        val a2 = success(GoldEngine.addIngot(spec, bd("748"), bd("1000"), ScalePrecision.G_0_01))
        val a3 = success(GoldEngine.addIngot(spec, bd("748"), bd("1000"), ScalePrecision.G_0_001))
        val a4 = success(GoldEngine.addIngot(spec, bd("748"), bd("1000"), ScalePrecision.G_0_0001))
        assertEquals("26.67", a2.practicalWeight.toPlainString())
        assertEquals("26.667", a3.practicalWeight.toPlainString())
        assertEquals("26.6667", a4.practicalWeight.toPlainString())
        // theoretical is untouched by rounding
        assertEquals("26.6666666667", r(a2.theoreticalWeight))
        assertEquals(a2.theoreticalWeight, a4.theoreticalWeight)
    }

    @Test fun practicalFinalKaratIsRecomputedFromRoundedWeight() {
        val a = success(GoldEngine.addIngot(spec, bd("748"), bd("1000"), ScalePrecision.G_0_01))
        // (142880 + 26.67*1000) / 226.67 = 748.0037...
        assertEquals("748.0037", r(a.practicalFinalKarat, 4))
        assertEquals("0.0037", r(a.deviation, 4))
        assertFalse(a.isDeviationNoticeable)
    }

    @Test fun smallBatchRoundingProducesNoticeableDeviation() {
        val a = success(GoldEngine.addIngot(listOf(ing("1", "500")), bd("700"), bd("1000"), ScalePrecision.G_0_01))
        assertEquals("0.67", a.practicalWeight.toPlainString())
        assertEquals("0.6667", r(a.theoreticalWeight, 4))
        assertTrue(a.isDeviationNoticeable)
        assertTrue(a.deviation.signum() > 0)
    }

    @Test fun cutPracticalWeightAndFinalKarat() {
        val a = success(GoldEngine.cutAndRefine(spec, bd("748"), ScalePrecision.G_0_01, selectedIndex = 0))
        val p = a.plan!!
        assertEquals("29.95", p.practicalCut.toPlainString())
        assertEquals("29.9465240642", r(p.theoreticalCut))
        // final karat recomputed with the rounded cut
        val expectedFinalW = bd("200").subtract(bd("29.95")).add(bd("29.95").multiply(bd("0.7")))
        assertEquals(expectedFinalW.setScale(6, RoundingMode.HALF_UP), p.practicalFinalWeight.setScale(6, RoundingMode.HALF_UP))
        assertTrue(p.deviation.signum() != 0)
    }

    @Test fun practicalCutNeverExceedsTheIngot() {
        // x = exactly the ingot weight with a weight that is not a multiple of the scale
        val a = success(GoldEngine.cutAndRefine(listOf(ing("0.004", "900")), bd("1000"), ScalePrecision.G_0_01))
        assertTrue(a.plan!!.practicalCut <= bd("0.004"))
    }

    // ------------------------------------------------------------ property: round-trip over random inputs

    @Test fun randomizedAddAlwaysHitsTheTarget() {
        val rnd = Random(20260101)
        var checked = 0
        repeat(2000) {
            val n = 1 + rnd.nextInt(6)
            val l = List(n) { Ingot(bd("%.3f".format(java.util.Locale.ROOT, 0.5 + rnd.nextDouble() * 500)), bd("%.1f".format(java.util.Locale.ROOT, rnd.nextDouble() * 1000))) }
            val target = bd("%.1f".format(java.util.Locale.ROOT, 1 + rnd.nextDouble() * 999))
            val ka = bd("%.1f".format(java.util.Locale.ROOT, rnd.nextDouble() * 1000))
            val out = GoldEngine.addIngot(l, target, ka)
            if (out is Outcome.Success) {
                val a = out.value
                assertTrue(a.theoreticalWeight.signum() > 0)
                assertTrue("final karat off target: ${a.theoreticalFinalKarat}", a.theoreticalFinalKarat.subtract(target).abs() < eps)
                checked++
            }
        }
        assertTrue("too few feasible random cases: $checked", checked > 300)
    }

    @Test fun randomizedCutAlwaysHitsTheTarget() {
        val rnd = Random(77)
        var checked = 0
        repeat(2000) {
            val n = 1 + rnd.nextInt(6)
            val l = List(n) { Ingot(bd("%.3f".format(java.util.Locale.ROOT, 0.5 + rnd.nextDouble() * 500)), bd("%.1f".format(java.util.Locale.ROOT, rnd.nextDouble() * 999))) }
            val target = bd("%.1f".format(java.util.Locale.ROOT, 1 + rnd.nextDouble() * 999))
            val out = GoldEngine.cutAndRefine(l, target)
            if (out is Outcome.Success) {
                val p = out.value.plan!!
                assertTrue(p.theoreticalCut.signum() > 0 && p.theoreticalCut <= p.candidate.ingot.weight)
                assertTrue("final karat off target: ${p.theoreticalFinalKarat}", p.theoreticalFinalKarat.subtract(target).abs() < eps)
                // best really is the minimum among feasible candidates
                val min = out.value.candidates.filter { it.status == CutStatus.OK }.minOf { it.requiredCut!! }
                assertEquals(0, min.compareTo(p.theoreticalCut))
                checked++
            }
        }
        assertTrue("too few feasible random cases: $checked", checked > 200)
    }
}
