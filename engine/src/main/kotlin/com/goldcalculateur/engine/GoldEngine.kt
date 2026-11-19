package com.goldcalculateur.engine

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Exact gold-alloy arithmetic. All inputs and intermediate values are BigDecimal.
 * Sums and products are exact; only divisions use [MC] (60 significant digits) and
 * NOTHING is rounded until the explicit "practical weight" step.
 *
 * Notation (karats are in thousandths, 0..1000):
 *   W  = total weight,  P = sum(w_i * k_i) = 1000 * G,  Kc = P / W
 *   add ingot (Ka):     Wa = W (Kt - Kc) / (Ka - Kt) = (Kt*W - P) / (Ka - Kt)
 *   cut + refine (Ki):  x  = W (Kt - Kc) / [Kt (1 - Ki/1000)]
 *                          = 1000 (Kt*W - P) / (Kt (1000 - Ki))
 */
object GoldEngine {

    val MC: MathContext = MathContext(60, RoundingMode.HALF_EVEN)
    val THOUSAND: BigDecimal = BigDecimal(1000)

    /** Deviation (per-mille) from which the UI must warn that rounding moved the result off target. */
    val NOTICEABLE_DEVIATION: BigDecimal = BigDecimal("0.01")

    private fun <T> ok(v: T): Outcome<T> = Outcome.Success(v)
    private fun fail(e: CalcError): Outcome.Failure = Outcome.Failure(e)

    fun summarize(ingots: List<Ingot>): Outcome<GroupSummary> {
        if (ingots.isEmpty()) return fail(CalcError.NO_INGOTS)
        var w = BigDecimal.ZERO
        var p = BigDecimal.ZERO
        for (ing in ingots) {
            if (ing.weight.signum() <= 0) return fail(CalcError.INVALID_WEIGHT)
            if (ing.karat.signum() < 0 || ing.karat > THOUSAND) return fail(CalcError.INVALID_KARAT)
            w = w.add(ing.weight)
            p = p.add(ing.weight.multiply(ing.karat))
        }
        val gold = p.movePointLeft(3) // exact: P / 1000
        val kc = p.divide(w, MC)
        return ok(GroupSummary(totalWeight = w, pureGold = gold, averageKarat = kc, goldProduct = p))
    }

    private fun validTarget(t: BigDecimal) = t.signum() > 0 && t <= THOUSAND

    /** Sign of (Kt*W - P): >0 target above current, 0 equal (exact), <0 below. */
    private fun gap(s: GroupSummary, target: BigDecimal): BigDecimal =
        target.multiply(s.totalWeight).subtract(s.goldProduct)

    private fun round(v: BigDecimal, p: ScalePrecision): BigDecimal =
        v.setScale(p.decimals, RoundingMode.HALF_UP)

    // ------------------------------------------------------------------ add ingot

    fun addIngot(
        ingots: List<Ingot>,
        target: BigDecimal,
        addedKarat: BigDecimal,
        precision: ScalePrecision = ScalePrecision.G_0_001,
    ): Outcome<AddResult> {
        val s = when (val r = summarize(ingots)) {
            is Outcome.Failure -> return r
            is Outcome.Success -> r.value
        }
        if (!validTarget(target)) return fail(CalcError.INVALID_TARGET)
        if (addedKarat.signum() < 0 || addedKarat > THOUSAND) return fail(CalcError.INVALID_ADDED_KARAT)

        val gap = gap(s, target)
        if (gap.signum() == 0) return fail(CalcError.TARGET_EQUALS_CURRENT)

        val denominator = addedKarat.subtract(target) // Ka - Kt
        if (denominator.signum() == 0) return fail(CalcError.NO_SOLUTION_SAME_KARAT)

        // Wa > 0 only if gap and (Ka - Kt) have the same sign.
        if (gap.signum() != denominator.signum()) return fail(CalcError.ADDED_KARAT_WRONG_DIRECTION)

        val wa = gap.divide(denominator, MC)
        val finalWeight = s.totalWeight.add(wa)
        val finalKarat = s.goldProduct.add(wa.multiply(addedKarat)).divide(finalWeight, MC)

        // Practical: measure Wa on the balance, then recompute the real final karat.
        val waR = round(wa, precision)
        val finalWeightR = s.totalWeight.add(waR)
        val finalKaratR = s.goldProduct.add(waR.multiply(addedKarat)).divide(finalWeightR, MC)

        return ok(
            AddResult(
                summary = s, target = target, addedKarat = addedKarat, precision = precision,
                theoreticalWeight = wa, theoreticalFinalWeight = finalWeight, theoreticalFinalKarat = finalKarat,
                practicalWeight = waR, practicalFinalWeight = finalWeightR, practicalFinalKarat = finalKaratR,
            )
        )
    }

    // ------------------------------------------------------------------ cut & refine

    /**
     * @param selectedIndex 0-based ingot chosen by the user; null = automatically the feasible
     *  ingot that needs the smallest cut.
     */
    fun cutAndRefine(
        ingots: List<Ingot>,
        target: BigDecimal,
        precision: ScalePrecision = ScalePrecision.G_0_001,
        selectedIndex: Int? = null,
    ): Outcome<CutAnalysis> {
        val s = when (val r = summarize(ingots)) {
            is Outcome.Failure -> return r
            is Outcome.Success -> r.value
        }
        if (!validTarget(target)) return fail(CalcError.INVALID_TARGET)

        val gap = gap(s, target)
        if (gap.signum() == 0) return fail(CalcError.TARGET_EQUALS_CURRENT)
        if (gap.signum() < 0) return fail(CalcError.TARGET_BELOW_CURRENT)

        val candidates = ingots.mapIndexed { idx, ing ->
            if (ing.karat.compareTo(THOUSAND) >= 0) {
                CutCandidate(idx, ing, null, CutStatus.PURE_INGOT)
            } else {
                val x = gap.multiply(THOUSAND)
                    .divide(target.multiply(THOUSAND.subtract(ing.karat)), MC)
                val status = if (x <= ing.weight) CutStatus.OK else CutStatus.INSUFFICIENT_WEIGHT
                CutCandidate(idx, ing, x, status)
            }
        }

        val best = candidates.filter { it.status == CutStatus.OK }
            .minByOrNull { it.requiredCut!! }
            ?: return fail(CalcError.CUT_NO_SINGLE_INGOT_SUFFICIENT)

        val sel = if (selectedIndex == null) best.index else selectedIndex
        if (sel !in candidates.indices) return fail(CalcError.CUT_INDEX_OUT_OF_RANGE)
        val chosen = candidates[sel]

        val plan = if (chosen.status == CutStatus.OK) buildPlan(s, chosen, target, precision) else null
        return ok(
            CutAnalysis(
                summary = s, target = target, candidates = candidates,
                bestIndex = best.index, selectedIndex = sel, plan = plan, selectedStatus = chosen.status,
            )
        )
    }

    private fun buildPlan(s: GroupSummary, c: CutCandidate, target: BigDecimal, precision: ScalePrecision): CutPlan {
        val ing = c.ingot
        val x = c.requiredCut!!
        // Refining the cut part removes only its non-gold content: gold P is unchanged.
        val recovered = x.multiply(ing.karat).movePointLeft(3)
        val remaining = ing.weight.subtract(x)
        val finalWeight = s.totalWeight.subtract(x).add(recovered)
        val finalKarat = s.goldProduct.divide(finalWeight, MC)

        // Practical: cut what the balance can measure (never more than the ingot holds).
        val xR = round(x, precision).min(ing.weight)
        val recoveredR = xR.multiply(ing.karat).movePointLeft(3)
        val remainingR = ing.weight.subtract(xR)
        val finalWeightR = s.totalWeight.subtract(xR).add(recoveredR)
        val finalKaratR = s.goldProduct.divide(finalWeightR, MC)

        return CutPlan(
            candidate = c, precision = precision,
            theoreticalCut = x, theoreticalRecoveredGold = recovered, theoreticalRemainingWeight = remaining,
            theoreticalFinalWeight = finalWeight, theoreticalFinalKarat = finalKarat,
            practicalCut = xR, practicalRecoveredGold = recoveredR, practicalRemainingWeight = remainingR,
            practicalFinalWeight = finalWeightR, practicalFinalKarat = finalKaratR,
            target = target,
        )
    }
}
