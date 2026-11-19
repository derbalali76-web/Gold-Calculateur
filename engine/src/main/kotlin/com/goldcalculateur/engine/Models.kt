package com.goldcalculateur.engine

import java.math.BigDecimal

/** One ingot: weight in grams (> 0) and karat in thousandths (0..1000). */
data class Ingot(val weight: BigDecimal, val karat: BigDecimal)

/** Scale precision: how many decimals the physical balance can resolve. */
enum class ScalePrecision(val decimals: Int) {
    G_0_01(2),
    G_0_001(3),
    G_0_0001(4);

    companion object {
        fun fromDecimals(decimals: Int): ScalePrecision =
            entries.firstOrNull { it.decimals == decimals } ?: G_0_001
    }
}

/**
 * Summary of the current group.
 * [goldProduct] = sum(weight * karat) = 1000 * pure gold (exact, never rounded).
 */
data class GroupSummary(
    val totalWeight: BigDecimal,
    val pureGold: BigDecimal,
    val averageKarat: BigDecimal,
    val goldProduct: BigDecimal,
)

enum class CalcError {
    NO_INGOTS,
    INVALID_WEIGHT,
    INVALID_KARAT,
    INVALID_TARGET,
    INVALID_ADDED_KARAT,
    TARGET_EQUALS_CURRENT,

    /** "Cut and refine" can only RAISE the karat. */
    TARGET_BELOW_CURRENT,

    /** Added karat equals the target while the group differs from it: no finite solution. */
    NO_SOLUTION_SAME_KARAT,

    /** Added karat is on the wrong side of the target (cannot move the group toward it). */
    ADDED_KARAT_WRONG_DIRECTION,

    /** No single ingot is heavy enough to be cut and refined to reach the target. */
    CUT_NO_SINGLE_INGOT_SUFFICIENT,
    CUT_INDEX_OUT_OF_RANGE,
}

sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: CalcError) : Outcome<Nothing>
}

/** Result of "add an ingot". Theoretical values are never replaced by rounded ones. */
data class AddResult(
    val summary: GroupSummary,
    val target: BigDecimal,
    val addedKarat: BigDecimal,
    val precision: ScalePrecision,
    // theoretical (full BigDecimal precision)
    val theoreticalWeight: BigDecimal,
    val theoreticalFinalWeight: BigDecimal,
    val theoreticalFinalKarat: BigDecimal,
    // practical (weight rounded to the balance precision, then everything recomputed)
    val practicalWeight: BigDecimal,
    val practicalFinalWeight: BigDecimal,
    val practicalFinalKarat: BigDecimal,
) {
    /** practicalFinalKarat - target (per-mille). */
    val deviation: BigDecimal get() = practicalFinalKarat.subtract(target)
    val isDeviationNoticeable: Boolean
        get() = deviation.abs() >= GoldEngine.NOTICEABLE_DEVIATION
}

enum class CutStatus { OK, INSUFFICIENT_WEIGHT, PURE_INGOT }

data class CutCandidate(
    val index: Int,
    val ingot: Ingot,
    /** Theoretical weight to cut; null when the ingot is pure (karat 1000: impurities cannot be removed). */
    val requiredCut: BigDecimal?,
    val status: CutStatus,
)

data class CutPlan(
    val candidate: CutCandidate,
    val precision: ScalePrecision,
    // theoretical
    val theoreticalCut: BigDecimal,
    val theoreticalRecoveredGold: BigDecimal,
    val theoreticalRemainingWeight: BigDecimal,
    val theoreticalFinalWeight: BigDecimal,
    val theoreticalFinalKarat: BigDecimal,
    // practical
    val practicalCut: BigDecimal,
    val practicalRecoveredGold: BigDecimal,
    val practicalRemainingWeight: BigDecimal,
    val practicalFinalWeight: BigDecimal,
    val practicalFinalKarat: BigDecimal,
    val target: BigDecimal,
) {
    val deviation: BigDecimal get() = practicalFinalKarat.subtract(target)
    val isDeviationNoticeable: Boolean
        get() = deviation.abs() >= GoldEngine.NOTICEABLE_DEVIATION
}

data class CutAnalysis(
    val summary: GroupSummary,
    val target: BigDecimal,
    val candidates: List<CutCandidate>,
    /** Feasible ingot needing the smallest cut. */
    val bestIndex: Int,
    val selectedIndex: Int,
    /** Null when the selected ingot is not usable (see [selectedStatus]). */
    val plan: CutPlan?,
    val selectedStatus: CutStatus,
)
