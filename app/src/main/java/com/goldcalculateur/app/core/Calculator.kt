package com.goldcalculateur.app.core

import com.goldcalculateur.engine.AddResult
import com.goldcalculateur.engine.CalcError
import com.goldcalculateur.engine.CutAnalysis
import com.goldcalculateur.engine.GoldEngine
import com.goldcalculateur.engine.GroupSummary
import com.goldcalculateur.engine.Ingot
import com.goldcalculateur.engine.Outcome
import com.goldcalculateur.engine.ScalePrecision
import java.math.BigDecimal

/** Why a calculation cannot run: either an input problem or an impossible adjustment. */
sealed interface UiError {
    data class Engine(val error: CalcError) : UiError
    data class RowIncomplete(val rowNumber: Int) : UiError
    data object TargetMissing : UiError
    data object AddedKaratMissing : UiError
}

sealed interface ResultUi {
    data class Added(val result: AddResult) : ResultUi

    /** [rowNumbers] maps engine ingot index -> 1-based row number shown in the table. */
    data class Cut(val analysis: CutAnalysis, val rowNumbers: List<Int>) : ResultUi

    /** [badRow] is the 0-based row index to highlight, when the problem is in a specific row. */
    data class Failure(val error: UiError, val badRow: Int? = null) : ResultUi

    val isSuccess: Boolean
        get() = this is Added || (this is Cut && analysis.plan != null)
}

/**
 * Turns what the user typed into an engine call. Pure Kotlin: the UI never does arithmetic.
 * Rows where both fields are blank are ignored; a half-filled row is an error.
 */
object CalculationRunner {

    private class Parsed(
        val ingots: List<Ingot>,
        val rowNumbers: List<Int>,
        val target: BigDecimal,
        val addedKarat: BigDecimal?,
    )

    private fun parseIngots(work: WorkState): Triple<List<Ingot>, List<Int>, ResultUi.Failure?> {
        val ingots = ArrayList<Ingot>()
        val numbers = ArrayList<Int>()
        for ((i, row) in work.rows.withIndex()) {
            if (row.isBlank) continue
            val w = InputParser.parse(row.weight)
            val k = InputParser.parse(row.karat)
            if (w == null || k == null) {
                return Triple(emptyList(), emptyList(), ResultUi.Failure(UiError.RowIncomplete(i + 1), i))
            }
            if (w.signum() <= 0) {
                return Triple(emptyList(), emptyList(), ResultUi.Failure(UiError.Engine(CalcError.INVALID_WEIGHT), i))
            }
            if (k > GoldEngine.THOUSAND) {
                return Triple(emptyList(), emptyList(), ResultUi.Failure(UiError.Engine(CalcError.INVALID_KARAT), i))
            }
            ingots += Ingot(w, k)
            numbers += i + 1
        }
        if (ingots.isEmpty()) {
            return Triple(emptyList(), emptyList(), ResultUi.Failure(UiError.Engine(CalcError.NO_INGOTS)))
        }
        return Triple(ingots, numbers, null)
    }

    /** Live total weight / weighted average karat; null while there is no valid ingot yet. */
    fun liveSummary(work: WorkState): GroupSummary? {
        val valid = work.rows.mapNotNull { row ->
            val w = InputParser.parse(row.weight)
            val k = InputParser.parse(row.karat)
            if (w != null && k != null && w.signum() > 0 && k <= GoldEngine.THOUSAND) Ingot(w, k) else null
        }
        if (valid.isEmpty()) return null
        return (GoldEngine.summarize(valid) as? Outcome.Success)?.value
    }

    fun run(work: WorkState, scale: ScalePrecision, selectedCutIndex: Int?): ResultUi {
        val (ingots, numbers, problem) = parseIngots(work)
        if (problem != null) return problem
        val target = InputParser.parse(work.target) ?: return ResultUi.Failure(UiError.TargetMissing)

        return when (work.mode) {
            CalcMode.ADD_INGOT -> {
                val ka = InputParser.parse(work.addedKarat) ?: return ResultUi.Failure(UiError.AddedKaratMissing)
                when (val o = GoldEngine.addIngot(ingots, target, ka, scale)) {
                    is Outcome.Success -> ResultUi.Added(o.value)
                    is Outcome.Failure -> ResultUi.Failure(UiError.Engine(o.error))
                }
            }
            CalcMode.CUT_REFINE -> {
                val sel = selectedCutIndex?.takeIf { it in ingots.indices }
                when (val o = GoldEngine.cutAndRefine(ingots, target, scale, sel)) {
                    is Outcome.Success -> ResultUi.Cut(o.value, numbers)
                    is Outcome.Failure -> ResultUi.Failure(UiError.Engine(o.error))
                }
            }
        }
    }
}
