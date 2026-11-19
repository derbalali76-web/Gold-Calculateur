package com.goldcalculateur.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
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
            Image(painterResource(R.drawable.art_ic_chart), contentDescription = null, modifier = Modifier.size(width = 28.dp, height = 22.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.result_title), color = MaterialTheme.extra.goldText,
                fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
            )
            if (result.isSuccess) {
                IconTextButton(Icons.Filled.ContentCopy, stringResource(R.string.copy_result), actions.onCopy, "copy_result")
                IconTextButton(Icons.Filled.Share, stringResource(R.string.share_result), actions.onShare, "share_result")
            }
        }
        Spacer(Modifier.height(8.dp))

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
    ResultTable {
        ResultRow(stringResource(R.string.r_added_karat), nf.trimmed(r.addedKarat, 3))
        ResultRow(
            stringResource(R.string.r_add_weight), "${nf.fixed(r.practicalWeight, d)} $g",
            big = true, tag = "add_weight",
        )
        ResultRow(stringResource(R.string.r_final_weight), "${nf.fixed(r.practicalFinalWeight, d)} $g")
        FinalKaratRow(nf.fixed(r.practicalFinalKarat, 3), r.isDeviationNoticeable)
    }
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

    val plan: CutPlan? = a.plan
    ResultTable {
        // Selected ingot: a dropdown inside the table (no extra screen, no long list of alternatives).
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(0.56f).padding(horizontal = 10.dp, vertical = 12.dp)) {
                Text(stringResource(R.string.r_selected_ingot), color = MaterialTheme.colorScheme.onBackground, fontSize = 17.sp)
            }
            VerticalDivider(color = Look.GridLine)
            Box(Modifier.weight(0.44f).padding(4.dp), contentAlignment = Alignment.Center) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, Look.GridLine, RoundedCornerShape(10.dp))
                        .clickable { menuOpen = true }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .testTag("cut_ingot_dropdown"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(ingotOption(res, a.selectedIndex, nf), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.extra.goldText)
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
        if (plan != null) {
            HorizontalDivider(color = Look.GridLine)
            ResultRow(stringResource(R.string.r_cut_weight), "${nf.fixed(plan.practicalCut, d)} $g", big = true, tag = "cut_weight")
            ResultRow(stringResource(R.string.r_recovered_gold), "${nf.fixed(plan.practicalRecoveredGold, d)} $g")
            ResultRow(stringResource(R.string.r_remaining_weight), "${nf.fixed(plan.practicalRemainingWeight, d)} $g")
            ResultRow(stringResource(R.string.r_final_weight), "${nf.fixed(plan.practicalFinalWeight, d)} $g")
            FinalKaratRow(nf.fixed(plan.practicalFinalKarat, 3), plan.isDeviationNoticeable)
        }
    }

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

/** Table + the gold nugget artwork beside it (like the mockup). */
@Composable
private fun ResultTable(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier
                .weight(1f)
                .clip(shape)
                .background(Look.TableCell)
                .border(1.dp, Look.GridLine, shape),
            content = content,
        )
        Spacer(Modifier.width(6.dp))
        Image(painterResource(R.drawable.art_nugget), contentDescription = null, modifier = Modifier.size(width = 64.dp, height = 60.dp))
    }
}

@Composable
private fun ResultRow(label: String, value: String, big: Boolean = false, tag: String? = null) {
    Column {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(0.56f).padding(horizontal = 10.dp, vertical = 9.dp)) {
                Text(label, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
            }
            VerticalDivider(color = Look.GridLine)
            Box(Modifier.weight(0.44f).padding(horizontal = 6.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
                Text(
                    value,
                    modifier = if (tag != null) Modifier.testTag(tag) else Modifier,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = if (big) 22.sp else 20.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    style = androidx.compose.ui.text.TextStyle(textDirection = TextDirection.Ltr),
                )
            }
        }
        HorizontalDivider(color = Look.GridLine)
    }
}

/** Final karat: green with a check when on target, amber when rounding moved it off target. */
@Composable
private fun FinalKaratRow(value: String, deviationNoticeable: Boolean) {
    val color = if (deviationNoticeable) MaterialTheme.extra.warning else MaterialTheme.extra.success
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(color.copy(alpha = 0.16f))
            .testTag("final_karat_row"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(0.56f).padding(horizontal = 10.dp, vertical = 10.dp)) {
            Text(
                stringResource(R.string.r_final_karat),
                color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
            )
        }
        VerticalDivider(color = color.copy(alpha = 0.6f))
        Row(
            Modifier.weight(0.44f).padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value, color = color, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("final_karat"),
                style = androidx.compose.ui.text.TextStyle(textDirection = TextDirection.Ltr),
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                if (deviationNoticeable) Icons.Filled.Warning else Icons.Filled.Check,
                contentDescription = null, tint = color, modifier = Modifier.size(24.dp),
            )
        }
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
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun IconTextButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, tag: String) {
    TextButton(onClick = onClick, modifier = Modifier.testTag(tag), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.extra.goldText, modifier = Modifier.size(20.dp))
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
