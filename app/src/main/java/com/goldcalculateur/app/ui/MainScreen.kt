package com.goldcalculateur.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldcalculateur.app.R
import com.goldcalculateur.app.core.CalcMode
import com.goldcalculateur.app.core.IngotRow
import com.goldcalculateur.app.core.InputParser
import com.goldcalculateur.app.core.NumberFormatter
import com.goldcalculateur.app.ui.theme.extra
import java.math.BigDecimal

/** Everything the main screen can ask for. Defaults are no-ops so UI tests can ignore what they don't use. */
class MainActions(
    val onWeightChange: (Long, String) -> Unit = { _, _ -> },
    val onKaratChange: (Long, String) -> Unit = { _, _ -> },
    val onAddRow: () -> Unit = {},
    val onRemoveRow: (Long) -> Unit = {},
    val onTargetChange: (String) -> Unit = {},
    val onModeChange: (CalcMode) -> Unit = {},
    val onAddedKaratChange: (String) -> Unit = {},
    val onSelectCutIngot: (Int) -> Unit = {},
    val onCalculate: () -> Unit = {},
    val onClearAll: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onCopy: () -> Unit = {},
    val onShare: () -> Unit = {},
)

/**
 * The single, complete main screen, laid out in mockup proportions (see [LocalU]):
 * header, framed block (ingot table, summary, target, method switch, added-karat chips, result card),
 * bottom controls and — below them — the ad slot ([bannerSlot], the real adaptive banner).
 */
@Composable
fun MainScreen(
    state: MainUiState,
    nf: NumberFormatter,
    actions: MainActions,
    modifier: Modifier = Modifier,
    bannerSlot: @Composable () -> Unit = {},
) {
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    BoxWithConstraints(modifier.fillMaxSize()) {
        val u = maxWidth.value / 1024f
        CompositionLocalProvider(LocalU provides u) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(if (MaterialTheme.extra.isDark) Look.ScreenGradientDark else Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background)))
                    .statusBarsPadding()
                    .imePadding(),
            ) {
                Header()

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 15.px()),
                ) {
                    val frameShape = RoundedCornerShape(30.px())
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(frameShape)
                            .background(if (MaterialTheme.extra.isDark) Look.FrameGradient else Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background)))
                            .border(3.px(), Look.GoldBorder, frameShape)
                            .padding(start = 17.px(), end = 17.px(), top = 10.px(), bottom = 16.px()),
                        verticalArrangement = Arrangement.spacedBy(13.px()),
                    ) {
                        IngotsCard(state.work.rows, state.errorRow, actions)
                        GoldButton(
                            onClick = actions.onAddRow,
                            modifier = Modifier.align(Alignment.CenterHorizontally).width(560.px()).height(88.px()).testTag("add_row"),
                            shape = RoundedCornerShape(20.px()),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = Look.OnGold, modifier = Modifier.size(44.px()))
                            Spacer(Modifier.width(10.px()))
                            Text(stringResource(R.string.add_ingot_row), color = Look.OnGold, fontSize = 42.spx(), fontWeight = FontWeight.Bold)
                        }
                        SummaryRow(state, nf)
                        TargetCard(state.work.target, actions.onTargetChange)
                        ModeSwitch(state.work.mode, actions.onModeChange)
                        if (state.work.mode == CalcMode.ADD_INGOT) {
                            AddedKaratCard(state.work.addedKarat, actions.onAddedKaratChange)
                        }
                        ResultCard(state, nf, actions)
                    }
                    Spacer(Modifier.height(10.px()))
                }

                BottomBar(
                    onSettings = actions.onOpenSettings,
                    onClear = { showClearDialog = true },
                    onCalculate = actions.onCalculate,
                    showAd = !imeVisible,
                    bannerSlot = bannerSlot,
                )
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.clear_confirm_title)) },
            text = { Text(stringResource(R.string.clear_confirm_text)) },
            confirmButton = {
                TextButton(
                    onClick = { showClearDialog = false; actions.onClearAll() },
                    modifier = Modifier.testTag("confirm_clear"),
                ) { Text(stringResource(R.string.confirm_clear), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun Header() {
    // Artwork stays at the same physical sides in every language, like the mockup.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(Modifier.fillMaxWidth().height(125.px())) {
            Image(
                painterResource(R.drawable.art_hdr_bars), contentDescription = null,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 25.px()).size(width = 150.px(), height = 105.px()),
            )
            Column(Modifier.align(Alignment.TopCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.app_name),
                    style = TextStyle(
                        brush = Look.GoldTextGradient, fontSize = 66.spx(), fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.SansSerif, fontStyle = FontStyle.Italic,
                    ),
                    maxLines = 1,
                )
                Text(
                    stringResource(R.string.tagline),
                    color = Look.Cream, fontSize = 40.spx(), maxLines = 1,
                )
            }
            Image(
                painterResource(R.drawable.art_hdr_scales), contentDescription = null,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 32.px()).size(width = 125.px(), height = 105.px()),
            )
        }
    }
}

// ------------------------------------------------------------------------------ ingot table

@Composable
private fun IngotsCard(rows: List<IngotRow>, errorRow: Int?, actions: MainActions) {
    val shape = RoundedCornerShape(22.px())
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Look.TableCell)
            .border(1.px(), Look.GridLine, shape),
    ) {
        Row(Modifier.fillMaxWidth().height(92.px()).background(Look.TableHeader)) {
            HeaderCell(stringResource(R.string.col_ingot), Modifier.weight(129f))
            VerticalDivider(color = Look.GridLine)
            HeaderCell(stringResource(R.string.col_weight), Modifier.weight(365f))
            VerticalDivider(color = Look.GridLine)
            HeaderCell(stringResource(R.string.col_karat), Modifier.weight(324f))
            VerticalDivider(color = Look.GridLine)
            Spacer(Modifier.weight(140f))
        }
        rows.forEachIndexed { index, row ->
            HorizontalDivider(color = Look.GridLine)
            key(row.id) {
                IngotRowView(number = index + 1, row = row, isError = errorRow == index, actions = actions)
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier) {
    Box(modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        Text(
            text,
            color = MaterialTheme.extra.goldText, fontSize = 32.spx(), fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun IngotRowView(number: Int, row: IngotRow, isError: Boolean, actions: MainActions) {
    Row(
        modifier = Modifier.fillMaxWidth().height(92.px()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(129f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Text(
                number.toString(),
                color = MaterialTheme.colorScheme.onSurface, fontSize = 44.spx(),
                textAlign = TextAlign.Center,
            )
        }
        VerticalDivider(color = Look.GridLine)
        CompactField(
            value = row.weight, onValueChange = { actions.onWeightChange(row.id, it) },
            placeholder = stringResource(R.string.hint_weight), isError = isError, plain = true,
            height = 92.px(), fontSize = 46.spx(),
            modifier = Modifier.weight(365f).testTag("weight_$number"),
        )
        VerticalDivider(color = Look.GridLine)
        CompactField(
            value = row.karat, onValueChange = { actions.onKaratChange(row.id, it) },
            placeholder = stringResource(R.string.hint_karat), isError = isError, plain = true,
            height = 92.px(), fontSize = 46.spx(),
            modifier = Modifier.weight(324f).testTag("karat_$number"),
        )
        VerticalDivider(color = Look.GridLine)
        Box(
            Modifier.weight(140f).fillMaxHeight().clickable { actions.onRemoveRow(row.id) }.testTag("delete_$number"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.cd_delete_ingot, number),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(54.px()),
            )
        }
    }
}

// ------------------------------------------------------------------------------ summary / target

@Composable
private fun SummaryRow(state: MainUiState, nf: NumberFormatter) {
    val g = stringResource(R.string.unit_g)
    val summary = state.liveSummary
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(17.px())) {
        SummaryCard(
            label = stringResource(R.string.summary_total_weight),
            value = summary?.let { "${nf.trimmed(it.totalWeight, 2)} $g" } ?: "—",
            art = R.drawable.art_sum_scales,
            modifier = Modifier.weight(470f).testTag("summary_total"),
        )
        SummaryCard(
            label = stringResource(R.string.summary_average_karat),
            // Weighted average, shown as a bare number (no "/1000").
            value = summary?.let { nf.trimmed(it.averageKarat, 2) } ?: "—",
            art = R.drawable.art_sum_bars,
            modifier = Modifier.weight(468f).testTag("summary_average"),
        )
    }
}

@Composable
private fun SummaryCard(label: String, value: String, art: Int, modifier: Modifier) {
    val shape = RoundedCornerShape(28.px())
    Row(
        modifier
            .height(150.px())
            .clip(shape)
            .background(if (MaterialTheme.extra.isDark) Look.CardGradientDark else Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface)))
            .border(2.px(), Look.GoldBorder, shape)
            .padding(horizontal = 20.px()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Look.Cream, fontSize = 32.spx(), maxLines = 1)
            Text(
                value, color = MaterialTheme.extra.goldText, fontSize = 56.spx(), fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Image(painterResource(art), contentDescription = null, modifier = Modifier.size(width = 90.px(), height = 78.px()))
    }
}

@Composable
private fun TargetCard(target: String, onChange: (String) -> Unit) {
    val shape = RoundedCornerShape(28.px())
    Row(
        Modifier
            .fillMaxWidth()
            .height(124.px())
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF00192A), Color(0xFF022037))))
            .border(1.px(), Look.GridLine, shape)
            .padding(horizontal = 18.px()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.art_ic_target), contentDescription = null, modifier = Modifier.size(56.px()))
        Spacer(Modifier.width(12.px()))
        Text(
            stringResource(R.string.target_karat),
            color = Look.Cream, fontSize = 38.spx(), fontWeight = FontWeight.Bold, maxLines = 1,
        )
        Spacer(Modifier.width(24.px()))
        CompactField(
            value = target, onValueChange = onChange, placeholder = "748",
            imeAction = ImeAction.Done, height = 92.px(), fontSize = 54.spx(), radius = 16.px(),
            modifier = Modifier.weight(1f).testTag("target"),
        )
        Spacer(Modifier.width(14.px()))
        KaratStepper(target, onChange, base = 750, modifier = Modifier.size(width = 80.px(), height = 92.px()))
    }
}

// ------------------------------------------------------------------------------ method switch

@Composable
private fun ModeSwitch(mode: CalcMode, onChange: (CalcMode) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(17.px())) {
        ModeButton(
            text = stringResource(R.string.mode_cut), art = R.drawable.art_ic_cut, selected = mode == CalcMode.CUT_REFINE,
            onClick = { onChange(CalcMode.CUT_REFINE) }, modifier = Modifier.weight(468f).testTag("mode_cut"),
        )
        ModeButton(
            text = stringResource(R.string.mode_add), art = R.drawable.art_ic_add, selected = mode == CalcMode.ADD_INGOT,
            onClick = { onChange(CalcMode.ADD_INGOT) }, modifier = Modifier.weight(465f).testTag("mode_add"),
        )
    }
}

@Composable
private fun ModeButton(text: String, art: Int, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    GoldButton(
        onClick = onClick, selected = selected, shape = RoundedCornerShape(20.px()),
        modifier = modifier.height(104.px()).semantics { this.selected = selected },
    ) {
        Image(painterResource(art), contentDescription = null, modifier = Modifier.size(width = 76.px(), height = 62.px()))
        Spacer(Modifier.width(10.px()))
        Text(
            text,
            color = if (selected) Look.OnGold else Look.Cream,
            fontSize = 38.spx(), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** (label shown, thousandths value) */
private val QuickKarats = listOf("18K" to "750", "21K" to "875", "22K" to "917", "24K" to "1000")

/** Shown only for the "add ingot" method: the karat to add, typed or picked from the chips. */
@Composable
private fun AddedKaratCard(addedKarat: String, onChange: (String) -> Unit) {
    val current: BigDecimal? = InputParser.parse(addedKarat)
    val shape = RoundedCornerShape(28.px())
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF00172A), Color(0xFF041F35))))
            .border(1.px(), Look.GridLine, shape)
            .padding(horizontal = 26.px(), vertical = 14.px()),
        verticalArrangement = Arrangement.spacedBy(12.px()),
    ) {
        Text(
            stringResource(R.string.added_karat), color = MaterialTheme.extra.goldText,
            fontSize = 38.spx(), fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(14.px()), verticalAlignment = Alignment.CenterVertically) {
            CompactField(
                value = addedKarat, onValueChange = onChange, placeholder = "1000",
                imeAction = ImeAction.Done, height = 92.px(), fontSize = 54.spx(), radius = 16.px(),
                modifier = Modifier.weight(1f).testTag("added_karat"),
            )
            KaratStepper(addedKarat, onChange, base = 1000, modifier = Modifier.size(width = 80.px(), height = 92.px()))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.px())) {
            QuickKarats.forEach { (label, value) ->
                val selected = current != null && current.compareTo(BigDecimal(value)) == 0
                QuickChip(label, selected, { onChange(value) }, Modifier.weight(1f).testTag("chip_$value"))
            }
        }
    }
}

@Composable
private fun QuickChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(12.px())
    Box(
        modifier = modifier
            .height(84.px())
            .clip(shape)
            .background(if (selected) Look.GoldGradient else Brush.verticalGradient(listOf(Color(0xFF012139), Color(0xFF011D35))))
            .border(1.px(), if (selected) Color(0xFFFFE680) else Look.FieldBorder, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = if (selected) Look.OnGold else Look.Cream,
            fontSize = 40.spx(), fontWeight = FontWeight.Bold, maxLines = 1,
        )
    }
}

// ------------------------------------------------------------------------------ bottom bar + ad slot

@Composable
private fun BottomBar(
    onSettings: () -> Unit,
    onClear: () -> Unit,
    onCalculate: () -> Unit,
    showAd: Boolean,
    bannerSlot: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 17.px(), end = 23.px(), top = 12.px(), bottom = 8.px()),
    ) {
        // Settings must stay at the physical far left in every language, so this row is always LTR.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.px()),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                BarButton(
                    art = R.drawable.art_ic_gear, label = stringResource(R.string.settings),
                    onClick = onSettings, modifier = Modifier.weight(224f).testTag("open_settings"),
                )
                BarButton(
                    art = R.drawable.art_ic_trash, label = stringResource(R.string.clear_all),
                    onClick = onClear, modifier = Modifier.weight(278f).testTag("clear_all"),
                )
                GoldButton(
                    onClick = onCalculate,
                    modifier = Modifier.weight(441f).height(120.px()).testTag("calculate"),
                    shape = RoundedCornerShape(28.px()),
                ) {
                    Text(stringResource(R.string.calculate), color = Look.OnGold, fontSize = 58.spx(), fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.width(18.px()))
                    Icon(Icons.Filled.Calculate, contentDescription = null, tint = Look.OnGold, modifier = Modifier.size(64.px()))
                }
            }
        }
        // The ad slot: the real AdMob adaptive banner inside the mockup's gold frame.
        if (showAd) {
            // Safety gap between the controls and the ad: never less than 16dp (prevents accidental clicks).
            Spacer(Modifier.height(maxOf(16.dp, 24.px())))
            val shape = RoundedCornerShape(28.px())
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Look.FrameGradient)
                    .border(4.px(), Look.GoldBorder, shape)
                    .padding(6.px()),
                contentAlignment = Alignment.Center,
            ) { bannerSlot() }
        }
    }
}

@Composable
private fun BarButton(art: Int, label: String, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(28.px())
    Column(
        modifier
            .height(120.px())
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF001A30), Color(0xFF00101D))))
            .border(2.px(), Look.GoldBorder, shape)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Image(painterResource(art), contentDescription = null, modifier = Modifier.size(58.px()))
        Text(label, color = Look.Cream, fontSize = 30.spx(), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
