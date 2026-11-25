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
import androidx.compose.ui.graphics.Brush
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
import java.math.BigDecimal

/**
 * One organised card with only the essential figures. Details can be expanded.
 * Rendered only when there is a valid result for the CURRENT inputs (edits clear it).
 */
@Composable
fun ResultCard(state: MainUiState, nf: NumberFormatter, actions: MainActions, modifier: Modifier = Modifier) {
    val result = state.result ?: return
    val cardShape = RoundedCornerShape(28.px())
    Column(
        modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(if (MaterialTheme.extra.isDark) Look.CardGradientDark else Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface)))
            .border(2.px(), Look.GoldBorder, cardShape)
            .padding(horizontal = 27.px(), vertical = 10.px())
            .testTag("result_card"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.art_ic_chart), contentDescription = null, modifier = Modifier.size(width = 44.px(), height = 36.px()))
            Spacer(Modifier.width(12.px()))
            Text(
                stringResource(R.string.result_title), color = MaterialTheme.extra.goldText,
                fontSize = 34.spx(), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
            )
            if (result.isSuccess) {
                IconTextButton(Icons.Filled.ContentCopy, stringResource(R.string.copy_result), actions.onCopy, "copy_result")
                IconTextButton(Icons.Filled.Share, stringResource(R.string.share_result), actions.onShare, "share_result")
            }
        }
        Spacer(Modifier.height(10.px()))

        when (result) {
            is ResultUi.Failure -> MessageBox(errorMessage(result.error), isError = true, tag = "result_error")
            is ResultUi.Added -> AddedBody(result.result, nf)
            is ResultUi.Cut -> CutBody(result, nf, actions)
        }
    }
}

// ------------------------------------------------------------------------------ add ingot

private const val DEC = 2

@Composable
private fun AddedBody(r: AddResult, nf: NumberFormatter) {
    val g = stringResource(R.string.unit_g)
    BigGreenResult(
        label = stringResource(R.string.r_add_weight),
        value = "${nf.fixed(r.practicalWeight, DEC)} $g",
        tag = "add_weight",
    )
    if (r.isDeviationNoticeable) {
        MessageBox(stringResource(R.string.warn_deviation, nf.fixed(r.deviation.abs(), DEC)), isError = false, tag = "deviation_warning")
    }
    Details(
        finalKarat = nf.fixed(r.practicalFinalKarat, DEC), deviationNoticeable = r.isDeviationNoticeable,
        rows = listOf(
            stringResource(R.string.r_added_karat) to nf.trimmed(r.addedKarat, DEC),
            stringResource(R.string.r_add_weight) to "${nf.fixed(r.practicalWeight, DEC)} $g",
            stringResource(R.string.r_final_weight) to "${nf.fixed(r.practicalFinalWeight, DEC)} $g",
            stringResource(R.string.d_theoretical_weight) to "${nf.fixed(r.theoreticalWeight, DEC)} $g",
            stringResource(R.string.d_deviation) to signed(r.deviation, nf),
            stringResource(R.string.d_group_gold) to "${nf.fixed(r.summary.pureGold, DEC)} $g",
        ),
    )
    Note(stringResource(R.string.note_add))
}

// ------------------------------------------------------------------------------ cut & refine

@Composable
private fun CutBody(res: ResultUi.Cut, nf: NumberFormatter, actions: MainActions) {
    val a = res.analysis
    val g = stringResource(R.string.unit_g)
    var menuOpen by remember { mutableStateOf(false) }
    val plan: CutPlan? = a.plan

    // Ingot picker ABOVE the result: a window to change which ingot is cut.
    val pickerShape = RoundedCornerShape(18.px())
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(pickerShape)
                .background(Look.FieldBg)
                .border(2.px(), Look.FieldBorder, pickerShape)
                .clickable { menuOpen = true }
                .padding(horizontal = 22.px(), vertical = 14.px())
                .testTag("cut_ingot_dropdown"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.r_selected_ingot), color = Look.Cream, fontSize = 28.spx())
                Text(
                    ingotOption(res, a.selectedIndex, nf),
                    color = MaterialTheme.colorScheme.onSurface, fontSize = 42.spx(), fontWeight = FontWeight.Bold,
                )
            }
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.extra.goldText, modifier = Modifier.size(56.px()))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            a.candidates.forEach { c ->
                DropdownMenuItem(
                    text = {
                        val usable = c.status == CutStatus.OK
                        Text(
                            ingotOption(res, c.index, nf) + if (usable) "" else "  ✕",
                            fontSize = 20.sp,
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
    Spacer(Modifier.height(14.px()))

    if (plan == null) {
        val candidate = a.candidates[a.selectedIndex]
        val message = when (a.selectedStatus) {
            CutStatus.INSUFFICIENT_WEIGHT -> stringResource(
                R.string.cut_insufficient,
                nf.fixed(candidate.requiredCut ?: BigDecimal.ZERO, DEC),
                nf.fixed(candidate.ingot.weight, DEC),
            )
            else -> stringResource(R.string.cut_pure)
        }
        MessageBox(message, isError = true, tag = "cut_unusable")
        return
    }

    BigGreenResult(
        label = stringResource(R.string.r_cut_weight),
        value = "${nf.fixed(plan.practicalCut, DEC)} $g",
        tag = "cut_weight",
    )
    if (plan.isDeviationNoticeable) {
        MessageBox(stringResource(R.string.warn_deviation, nf.fixed(plan.deviation.abs(), DEC)), isError = false, tag = "deviation_warning")
    }
    Details(
        finalKarat = nf.fixed(plan.practicalFinalKarat, DEC), deviationNoticeable = plan.isDeviationNoticeable,
        rows = listOf(
            stringResource(R.string.r_selected_ingot) to ingotOption(res, a.selectedIndex, nf),
            stringResource(R.string.r_cut_weight) to "${nf.fixed(plan.practicalCut, DEC)} $g",
            stringResource(R.string.r_recovered_gold) to "${nf.fixed(plan.practicalRecoveredGold, DEC)} $g",
            stringResource(R.string.r_remaining_weight) to "${nf.fixed(plan.practicalRemainingWeight, DEC)} $g",
            stringResource(R.string.r_final_weight) to "${nf.fixed(plan.practicalFinalWeight, DEC)} $g",
            stringResource(R.string.d_theoretical_weight) to "${nf.fixed(plan.theoreticalCut, DEC)} $g",
            stringResource(R.string.d_deviation) to signed(plan.practicalFinalKarat.subtract(plan.target), nf),
            stringResource(R.string.d_group_gold) to "${nf.fixed(a.summary.pureGold, DEC)} $g",
        ),
    )
    Note(stringResource(R.string.note_cut))
}

@Composable
private fun ingotOption(res: ResultUi.Cut, engineIndex: Int, nf: NumberFormatter): String {
    val c = res.analysis.candidates[engineIndex]
    return stringResource(R.string.ingot_option, res.rowNumbers[engineIndex], nf.trimmed(c.ingot.karat, DEC))
}

// ------------------------------------------------------------------------------ pieces

/** The one number the jeweller acts on: big, bold, green. */
@Composable
private fun BigGreenResult(label: String, value: String, tag: String) {
    val shape = RoundedCornerShape(20.px())
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Look.GreenRowBg)
            .border(2.px(), Look.GreenRowBorder, shape)
            .padding(horizontal = 22.px(), vertical = 16.px()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Look.Cream, fontSize = 32.spx(), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(
                value,
                modifier = Modifier.testTag(tag),
                color = MaterialTheme.extra.success, fontSize = 84.spx(), fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center, maxLines = 1,
                style = androidx.compose.ui.text.TextStyle(textDirection = TextDirection.Ltr),
            )
        }
        Image(painterResource(R.drawable.art_nugget), contentDescription = null, modifier = Modifier.size(width = 130.px(), height = 100.px()))
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Column {
        Row(Modifier.fillMaxWidth().height(72.px()), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 18.px()), contentAlignment = Alignment.CenterStart) {
                Text(label, color = Look.Cream, fontSize = 30.spx(), maxLines = 2)
            }
            VerticalDivider(color = Look.GridLine)
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Text(
                    value,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 36.spx(), fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center, maxLines = 1,
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
    val shape = RoundedCornerShape(12.px())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.px())
            .clip(shape)
            .background(if (deviationNoticeable) color.copy(alpha = 0.16f) else Look.GreenRowBg)
            .border(1.px(), if (deviationNoticeable) color else Look.GreenRowBorder, shape)
            .testTag("final_karat_row"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 18.px()), contentAlignment = Alignment.CenterStart) {
            Text(
                stringResource(R.string.r_final_karat),
                color = Look.Cream, fontSize = 34.spx(), fontWeight = FontWeight.Bold, maxLines = 1,
            )
        }
        Row(
            Modifier.weight(1f).fillMaxHeight(),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value, color = color, fontSize = 46.spx(), fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("final_karat"),
                style = androidx.compose.ui.text.TextStyle(textDirection = TextDirection.Ltr),
            )
            Spacer(Modifier.width(14.px()))
            Icon(
                if (deviationNoticeable) Icons.Filled.Warning else Icons.Filled.Check,
                contentDescription = null, tint = color, modifier = Modifier.size(40.px()),
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
private fun Details(rows: List<Pair<String, String>>, finalKarat: String, deviationNoticeable: Boolean) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.px())
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.px())
            .clip(shape)
            .border(1.px(), Look.GridLine, shape)
            .clickable { expanded = !expanded }
            .padding(vertical = 14.px(), horizontal = 18.px())
            .testTag("toggle_details"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(if (expanded) R.string.details_hide else R.string.details_show),
            color = MaterialTheme.extra.goldText, fontSize = 34.spx(), fontWeight = FontWeight.SemiBold,
        )
        Icon(
            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null, tint = MaterialTheme.extra.goldText, modifier = Modifier.size(46.px()),
        )
    }
    if (expanded) {
        val tableShape = RoundedCornerShape(12.px())
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.px())
                .clip(tableShape)
                .background(Look.ResultCell)
                .border(1.px(), Look.GridLine, tableShape)
                .testTag("details"),
        ) {
            rows.forEach { (label, value) -> ResultRow(label, value) }
            FinalKaratRow(finalKarat, deviationNoticeable)
        }
    }
}

@Composable
private fun Note(text: String) {
    Row(Modifier.padding(top = 14.px()), verticalAlignment = Alignment.CenterVertically) {
        Text(text, modifier = Modifier.weight(1f), color = Look.Cream, fontSize = 26.spx())
        Spacer(Modifier.width(10.px()))
        Icon(Icons.Filled.Info, contentDescription = null, tint = Look.Cream, modifier = Modifier.size(38.px()))
    }
}

@Composable
private fun IconTextButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, tag: String) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.extra.goldText, modifier = Modifier.size(18.dp))
    }
}


private fun signed(v: BigDecimal, nf: NumberFormatter): String =
    (if (v.signum() > 0) "+" else "") + nf.fixed(v, 2)

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
