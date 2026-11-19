package com.goldcalculateur.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldcalculateur.app.R
import com.goldcalculateur.app.core.NumberFormatter
import com.goldcalculateur.app.core.ResultUi
import com.goldcalculateur.app.core.UiError
import com.goldcalculateur.app.ui.theme.extra
import com.goldcalculateur.engine.AddResult
import com.goldcalculateur.engine.CalcError
import com.goldcalculateur.engine.CutPlan
import com.goldcalculateur.engine.CutStatus
import com.goldcalculateur.engine.ScalePrecision
import java.math.BigDecimal

/**
 * One organised card with only the essential figures. Details can be expanded.
 * Rendered only when there is a valid result for the CURRENT inputs (edits clear it).
 */
@Composable
fun ResultCard(state: MainUiState, nf: NumberFormatter, actions: MainActions, modifier: Modifier = Modifier) {
    val result = state.result ?: return
    SectionCard(modifier.testTag("result_card")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.extra.goldText, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.result_title), color = MaterialTheme.extra.goldText,
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
            )
            if (result.isSuccess) {
                IconTextButton(Icons.Filled.ContentCopy, stringResource(R.string.copy_result), actions.onCopy, "copy_result")
                IconTextButton(Icons.Filled.Share, stringResource(R.string.share_result), actions.onShare, "share_result")
            }
        }
        Spacer(Modifier.height(4.dp))

        when (result) {
            is ResultUi.Failure -> MessageBox(errorMessage(result.error), isError = true, tag = "result_error")
            is ResultUi.Added -> AddedBody(result.result, state.scale, nf)
            is ResultUi.Cut -> CutBody(result, state.scale, nf, actions)
        }
    }
}

// ------------------------------------------------------------------------------ add ingot

@Composable
private fun AddedBody(r: AddResult, scale: ScalePrecision, nf: NumberFormatter) {
    val g = stringResource(R.string.unit_g)
    val d = scale.decimals
    ResultRow(stringResource(R.string.r_added_karat), nf.trimmed(r.addedKarat, 3))
    ResultRow(
        stringResource(R.string.r_add_weight), "${nf.fixed(r.practicalWeight, d)} $g",
        big = true, tag = "add_weight",
    )
    ResultRow(stringResource(R.string.r_final_weight), "${nf.fixed(r.practicalFinalWeight, d)} $g")
    FinalKaratRow(nf.fixed(r.practicalFinalKarat, 3), r.isDeviationNoticeable)
    if (r.isDeviationNoticeable) {
        MessageBox(stringResource(R.string.warn_deviation, nf.fixed(r.deviation.abs(), 3)), isError = false, tag = "deviation_warning")
    }
    Details(
        rows = listOf(
            stringResource(R.string.d_theoretical_weight) to "${nf.trimmed(r.theoreticalWeight, 10)} $g",
            stringResource(R.string.d_practical_weight) to "${nf.fixed(r.practicalWeight, d)} $g",
            stringResource(R.string.d_scale_precision) to scaleLabel(scale),
            stringResource(R.string.d_actual_karat) to nf.fixed(r.practicalFinalKarat, 6),
            stringResource(R.string.d_deviation) to signed(r.deviation, nf),
            stringResource(R.string.d_group_gold) to "${nf.trimmed(r.summary.pureGold, 6)} $g",
        ),
    )
    Note(stringResource(R.string.note_add))
}

// ------------------------------------------------------------------------------ cut & refine

@Composable
private fun CutBody(res: ResultUi.Cut, scale: ScalePrecision, nf: NumberFormatter, actions: MainActions) {
    val a = res.analysis
    val g = stringResource(R.string.unit_g)
    val d = scale.decimals
    var menuOpen by remember { mutableStateOf(false) }

    // Selected ingot: a dropdown inside the card (no extra screen, no long list of alternatives).
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.r_selected_ingot), modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp,
        )
        Box {
            OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("cut_ingot_dropdown")) {
                Text(ingotOption(res, a.selectedIndex, nf), fontWeight = FontWeight.SemiBold)
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                a.candidates.forEach { c ->
                    DropdownMenuItem(
                        text = {
                            val usable = c.status == CutStatus.OK
                            Text(
                                ingotOption(res, c.index, nf) + if (usable) "" else "  ✕",
                                color = if (usable) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = { menuOpen = false; actions.onSelectCutIngot(c.index) },
                        modifier = Modifier.testTag("cut_option_${c.index}"),
                    )
                }
            }
        }
    }

    val plan: CutPlan? = a.plan
    if (plan == null) {
        val candidate = a.candidates[a.selectedIndex]
        val message = when (a.selectedStatus) {
            CutStatus.INSUFFICIENT_WEIGHT -> stringResource(
                R.string.cut_insufficient,
                nf.trimmed(candidate.requiredCut ?: BigDecimal.ZERO, 4),
                nf.trimmed(candidate.ingot.weight, 4),
            )
            else -> stringResource(R.string.cut_pure)
        }
        MessageBox(message, isError = true, tag = "cut_unusable")
        return
    }

    ResultRow(stringResource(R.string.r_cut_weight), "${nf.fixed(plan.practicalCut, d)} $g", big = true, tag = "cut_weight")
    ResultRow(stringResource(R.string.r_recovered_gold), "${nf.fixed(plan.practicalRecoveredGold, d)} $g")
    ResultRow(stringResource(R.string.r_remaining_weight), "${nf.fixed(plan.practicalRemainingWeight, d)} $g")
    ResultRow(stringResource(R.string.r_final_weight), "${nf.fixed(plan.practicalFinalWeight, d)} $g")
    FinalKaratRow(nf.fixed(plan.practicalFinalKarat, 3), plan.isDeviationNoticeable)
    if (plan.isDeviationNoticeable) {
        MessageBox(stringResource(R.string.warn_deviation, nf.fixed(plan.deviation.abs(), 3)), isError = false, tag = "deviation_warning")
    }
    Details(
        rows = listOf(
            stringResource(R.string.d_theoretical_weight) to "${nf.trimmed(plan.theoreticalCut, 10)} $g",
            stringResource(R.string.d_practical_weight) to "${nf.fixed(plan.practicalCut, d)} $g",
            stringResource(R.string.d_scale_precision) to scaleLabel(scale),
            stringResource(R.string.d_actual_karat) to nf.fixed(plan.practicalFinalKarat, 6),
            stringResource(R.string.d_deviation) to signed(plan.practicalFinalKarat.subtract(plan.target), nf),
            stringResource(R.string.d_group_gold) to "${nf.trimmed(a.summary.pureGold, 6)} $g",
        ),
    )
    Note(stringResource(R.string.note_cut))
}

@Composable
private fun ingotOption(res: ResultUi.Cut, engineIndex: Int, nf: NumberFormatter): String {
    val c = res.analysis.candidates[engineIndex]
    return stringResource(R.string.ingot_option, res.rowNumbers[engineIndex], nf.trimmed(c.ingot.karat, 3))
}

// ------------------------------------------------------------------------------ pieces

@Composable
private fun ResultRow(label: String, value: String, big: Boolean = false, tag: String? = null) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                value,
                modifier = if (tag != null) Modifier.testTag(tag) else Modifier,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = if (big) 26.sp else 20.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                style = androidx.compose.ui.text.TextStyle(textDirection = TextDirection.Ltr),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    }
}

/** Final karat: green with a check when on target, amber when rounding moved it off target. */
@Composable
private fun FinalKaratRow(value: String, deviationNoticeable: Boolean) {
    val color = if (deviationNoticeable) MaterialTheme.extra.warning else MaterialTheme.extra.success
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(color.copy(alpha = 0.14f), shape)
            .border(1.dp, color, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("final_karat_row"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (deviationNoticeable) Icons.Filled.Warning else Icons.Filled.Check,
            contentDescription = null, tint = color, modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.r_final_karat), modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
        )
        Text(
            value, color = color, fontSize = 30.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("final_karat"),
            style = androidx.compose.ui.text.TextStyle(textDirection = TextDirection.Ltr),
        )
    }
}

@Composable
private fun MessageBox(text: String, isError: Boolean, tag: String) {
    val color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.extra.warning
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(color.copy(alpha = 0.12f), shape)
            .border(1.dp, color, shape)
            .padding(12.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = color)
        Spacer(Modifier.width(10.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
    }
}

@Composable
private fun Details(rows: List<Pair<String, String>>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("toggle_details")) {
        Text(stringResource(if (expanded) R.string.details_hide else R.string.details_show), color = MaterialTheme.extra.goldText)
        Icon(
            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null, tint = MaterialTheme.extra.goldText,
        )
    }
    if (expanded) {
        Column(Modifier.testTag("details")) {
            rows.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        value, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        style = androidx.compose.ui.text.TextStyle(textDirection = TextDirection.Ltr),
                    )
                }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Filled.Info, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp).padding(top = 2.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

@Composable
private fun IconTextButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, tag: String) {
    TextButton(onClick = onClick, modifier = Modifier.testTag(tag)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.extra.goldText, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = MaterialTheme.extra.goldText, fontSize = 13.sp)
    }
}

@Composable
private fun scaleLabel(scale: ScalePrecision): String = stringResource(
    when (scale) {
        ScalePrecision.G_0_01 -> R.string.scale_2
        ScalePrecision.G_0_001 -> R.string.scale_3
        ScalePrecision.G_0_0001 -> R.string.scale_4
    }
)

private fun signed(v: BigDecimal, nf: NumberFormatter): String =
    (if (v.signum() > 0) "+" else "") + nf.fixed(v, 4)

@Composable
fun errorMessage(e: UiError): String = when (e) {
    is UiError.RowIncomplete -> stringResource(R.string.err_row_incomplete, e.rowNumber)
    UiError.TargetMissing -> stringResource(R.string.err_target_missing)
    UiError.AddedKaratMissing -> stringResource(R.string.err_added_karat_missing)
    is UiError.Engine -> stringResource(
        when (e.error) {
            CalcError.NO_INGOTS -> R.string.err_no_ingots
            CalcError.INVALID_WEIGHT -> R.string.err_invalid_weight
            CalcError.INVALID_KARAT -> R.string.err_invalid_karat
            CalcError.INVALID_TARGET -> R.string.err_invalid_target
            CalcError.INVALID_ADDED_KARAT -> R.string.err_invalid_added_karat
            CalcError.TARGET_EQUALS_CURRENT -> R.string.err_target_equals_current
            CalcError.TARGET_BELOW_CURRENT -> R.string.err_target_below_current
            CalcError.NO_SOLUTION_SAME_KARAT -> R.string.err_no_solution_same_karat
            CalcError.ADDED_KARAT_WRONG_DIRECTION -> R.string.err_wrong_direction
            CalcError.CUT_NO_SINGLE_INGOT_SUFFICIENT -> R.string.err_cut_none
            CalcError.CUT_INDEX_OUT_OF_RANGE -> R.string.err_generic
        }
    )
}
