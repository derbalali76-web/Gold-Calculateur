package com.goldcalculateur.app.ui

import android.content.Context
import com.goldcalculateur.app.R
import com.goldcalculateur.app.core.InputParser
import com.goldcalculateur.app.core.NumberFormatter
import com.goldcalculateur.app.core.ResultUi

/** Plain-text summary used by "Copy" and "Share". Null when there is no successful result. */
fun buildResultText(context: Context, nf: NumberFormatter, state: MainUiState): String? {
    val result = state.result ?: return null
    val d = state.scale.decimals
    val g = context.getString(R.string.unit_g)
    val lines = ArrayList<String>()
    lines += context.getString(R.string.share_header)
    state.liveSummary?.let {
        lines += context.getString(R.string.share_group, nf.trimmed(it.totalWeight, 2), nf.trimmed(it.averageKarat, 2))
    }
    InputParser.parse(state.work.target)?.let {
        lines += context.getString(R.string.share_target, nf.trimmed(it, 2))
    }
    when (result) {
        is ResultUi.Added -> {
            val r = result.result
            lines += context.getString(R.string.mode_add)
            lines += "${context.getString(R.string.r_added_karat)}: ${nf.trimmed(r.addedKarat, 2)}"
            lines += "${context.getString(R.string.r_add_weight)}: ${nf.fixed(r.practicalWeight, d)} $g"
            lines += "${context.getString(R.string.r_final_weight)}: ${nf.fixed(r.practicalFinalWeight, d)} $g"
            lines += "${context.getString(R.string.r_final_karat)}: ${nf.fixed(r.practicalFinalKarat, 2)}"
        }
        is ResultUi.Cut -> {
            val plan = result.analysis.plan ?: return null
            val label = context.getString(
                R.string.ingot_option, result.rowNumbers[result.analysis.selectedIndex],
                nf.trimmed(plan.candidate.ingot.karat, 2),
            )
            lines += context.getString(R.string.mode_cut)
            lines += "${context.getString(R.string.r_selected_ingot)}: $label"
            lines += "${context.getString(R.string.r_cut_weight)}: ${nf.fixed(plan.practicalCut, d)} $g"
            lines += "${context.getString(R.string.r_recovered_gold)}: ${nf.fixed(plan.practicalRecoveredGold, d)} $g"
            lines += "${context.getString(R.string.r_remaining_weight)}: ${nf.fixed(plan.practicalRemainingWeight, d)} $g"
            lines += "${context.getString(R.string.r_final_weight)}: ${nf.fixed(plan.practicalFinalWeight, d)} $g"
            lines += "${context.getString(R.string.r_final_karat)}: ${nf.fixed(plan.practicalFinalKarat, 2)}"
        }
        is ResultUi.Failure -> return null
    }
    lines += context.getString(R.string.share_footer)
    return lines.joinToString("\n")
}
