package com.goldcalculateur.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
 * The single, complete main screen:
 * header, ingot table, summary, target, method switch, (added-karat chips), result card,
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

    Column(
        modifier
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
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IngotsCard(state.work.rows, state.errorRow, actions)
            GoldButton(
                onClick = actions.onAddRow,
                modifier = Modifier.align(Alignment.CenterHorizontally).width(230.dp).height(48.dp).testTag("add_row"),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Look.OnGold)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_ingot_row), color = Look.OnGold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            SummaryRow(state, nf)
            TargetCard(state.work.target, actions.onTargetChange)
            ModeSwitch(state.work.mode, actions.onModeChange)
            if (state.work.mode == CalcMode.ADD_INGOT) {
                AddedKaratCard(state.work.addedKarat, actions.onAddedKaratChange)
            }
            ResultCard(state, nf, actions)
            Spacer(Modifier.height(6.dp))
        }

        BottomBar(
            onSettings = actions.onOpenSettings,
            onClear = { showClearDialog = true },
            onCalculate = actions.onCalculate,
            showAd = !imeVisible,
            bannerSlot = bannerSlot,
        )
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(painterResource(R.drawable.art_hdr_bars), contentDescription = null, modifier = Modifier.width(78.dp).height(54.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.app_name),
                    style = TextStyle(
                        brush = Look.GoldTextGradient, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic,
                    ),
                    maxLines = 1,
                )
                Text(
                    stringResource(R.string.tagline),
                    color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, maxLines = 1,
                )
            }
            Image(painterResource(R.drawable.art_hdr_scales), contentDescription = null, modifier = Modifier.width(62.dp).height(54.dp))
        }
    }
}

// ------------------------------------------------------------------------------ ingot table

private val CellHeight = 50.dp

@Composable
private fun IngotsCard(rows: List<IngotRow>, errorRow: Int?, actions: MainActions) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Look.TableCell)
            .border(1.dp, Look.GridLine, shape),
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(Look.TableHeader)) {
            HeaderCell(stringResource(R.string.col_ingot), Modifier.weight(0.16f))
            VerticalDivider(color = Look.GridLine)
            HeaderCell(stringResource(R.string.col_weight), Modifier.weight(0.38f))
            VerticalDivider(color = Look.GridLine)
            HeaderCell(stringResource(R.string.col_karat), Modifier.weight(0.30f))
            VerticalDivider(color = Look.GridLine)
            Spacer(Modifier.weight(0.16f))
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
    Box(modifier.fillMaxHeight().padding(vertical = 10.dp, horizontal = 4.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            color = MaterialTheme.extra.goldText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun IngotRowView(number: Int, row: IngotRow, isError: Boolean, actions: MainActions) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(0.16f).height(CellHeight), contentAlignment = Alignment.Center) {
            Text(
                number.toString(),
                color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
        VerticalDivider(color = Look.GridLine)
        CompactField(
            value = row.weight, onValueChange = { actions.onWeightChange(row.id, it) },
            placeholder = stringResource(R.string.hint_weight), isError = isError, plain = true,
            height = CellHeight, fontSize = 20,
            modifier = Modifier.weight(0.38f).testTag("weight_$number"),
        )
        VerticalDivider(color = Look.GridLine)
        CompactField(
            value = row.karat, onValueChange = { actions.onKaratChange(row.id, it) },
            placeholder = stringResource(R.string.hint_karat), isError = isError, plain = true,
            height = CellHeight, fontSize = 20,
            modifier = Modifier.weight(0.30f).testTag("karat_$number"),
        )
        VerticalDivider(color = Look.GridLine)
        IconButton(
            onClick = { actions.onRemoveRow(row.id) },
            modifier = Modifier.weight(0.16f).testTag("delete_$number"),
        ) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.cd_delete_ingot, number),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

// ------------------------------------------------------------------------------ summary / target

@Composable
private fun SummaryRow(state: MainUiState, nf: NumberFormatter) {
    val g = stringResource(R.string.unit_g)
    val summary = state.liveSummary
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SummaryCard(
            label = stringResource(R.string.summary_total_weight),
            value = summary?.let { "${nf.trimmed(it.totalWeight, 4)} $g" } ?: "—",
            art = R.drawable.art_sum_scales,
            modifier = Modifier.weight(1f).testTag("summary_total"),
        )
        SummaryCard(
            label = stringResource(R.string.summary_average_karat),
            // Weighted average, shown as a bare number (no "/1000").
            value = summary?.let { nf.trimmed(it.averageKarat, 3) } ?: "—",
            art = R.drawable.art_sum_bars,
            modifier = Modifier.weight(1f).testTag("summary_average"),
        )
    }
}

@Composable
private fun SummaryCard(label: String, value: String, art: Int, modifier: Modifier) {
    SectionCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, color = MaterialTheme.colorScheme.onBackground, fontSize = 17.sp, maxLines = 1)
                Text(
                    value, color = MaterialTheme.extra.goldText, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Image(painterResource(art), contentDescription = null, modifier = Modifier.size(width = 44.dp, height = 46.dp))
        }
    }
}

@Composable
private fun TargetCard(target: String, onChange: (String) -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Look.TableCell)
            .border(1.dp, Look.GridLine, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.art_ic_target), contentDescription = null, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.target_karat),
            color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        )
        Spacer(Modifier.width(12.dp))
        CompactField(
            value = target, onValueChange = onChange, placeholder = "748",
            imeAction = ImeAction.Done, height = 50.dp, fontSize = 26,
            modifier = Modifier.weight(1f).testTag("target"),
        )
        Spacer(Modifier.width(8.dp))
        KaratStepper(target, onChange, base = 750, modifier = Modifier.height(50.dp))
    }
}

// ------------------------------------------------------------------------------ method switch

@Composable
private fun ModeSwitch(mode: CalcMode, onChange: (CalcMode) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ModeButton(
            text = stringResource(R.string.mode_cut), art = R.drawable.art_ic_cut, selected = mode == CalcMode.CUT_REFINE,
            onClick = { onChange(CalcMode.CUT_REFINE) }, modifier = Modifier.weight(1f).testTag("mode_cut"),
        )
        ModeButton(
            text = stringResource(R.string.mode_add), art = R.drawable.art_ic_add, selected = mode == CalcMode.ADD_INGOT,
            onClick = { onChange(CalcMode.ADD_INGOT) }, modifier = Modifier.weight(1f).testTag("mode_add"),
        )
    }
}

@Composable
private fun ModeButton(text: String, art: Int, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    GoldButton(
        onClick = onClick, selected = selected, shape = RoundedCornerShape(16.dp),
        modifier = modifier.height(58.dp).semantics { this.selected = selected },
    ) {
        Image(painterResource(art), contentDescription = null, modifier = Modifier.size(width = 42.dp, height = 36.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            color = if (selected) Look.OnGold else MaterialTheme.colorScheme.onSurface,
            fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

private val QuickKarats = listOf("800", "850", "875", "900", "950", "1000")

/** Shown only for the "add ingot" method. Changing it refreshes the same screen and result in place. */
@Composable
private fun AddedKaratCard(addedKarat: String, onChange: (String) -> Unit) {
    val current: BigDecimal? = InputParser.parse(addedKarat)
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Look.TableCell)
            .border(1.dp, Look.GridLine, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            stringResource(R.string.added_karat), color = MaterialTheme.extra.goldText,
            fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            CompactField(
                value = addedKarat, onValueChange = onChange, placeholder = "1000",
                imeAction = ImeAction.Done, height = 44.dp,
                modifier = Modifier.width(84.dp).testTag("added_karat"),
            )
            KaratStepper(addedKarat, onChange, base = 1000, modifier = Modifier.height(44.dp))
            QuickKarats.forEach { preset ->
                val selected = current != null && current.compareTo(BigDecimal(preset)) == 0
                QuickChip(preset, selected, { onChange(preset) }, Modifier.weight(1f).testTag("chip_$preset"))
            }
        }
    }
}

@Composable
private fun QuickChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(if (selected) Look.GoldGradient else Brush.verticalGradient(listOf(Look.TableHeader, Look.TableCell)))
            .border(1.dp, if (selected) Color(0xFFFFE680) else Look.GridLine, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = if (selected) Look.OnGold else MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
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
    Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 12.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 6.dp),
        ) {
            // Settings must stay at the physical far left in every language, so this row is always LTR.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    BarButton(
                        icon = Icons.Filled.Settings, label = stringResource(R.string.settings),
                        onClick = onSettings, modifier = Modifier.width(78.dp).testTag("open_settings"),
                    )
                    BarButton(
                        icon = Icons.Filled.Delete, label = stringResource(R.string.clear_all),
                        onClick = onClear, modifier = Modifier.width(96.dp).testTag("clear_all"),
                    )
                    GoldButton(
                        onClick = onCalculate,
                        modifier = Modifier.weight(1f).height(60.dp).testTag("calculate"),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text(stringResource(R.string.calculate), color = Look.OnGold, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.width(10.dp))
                        Icon(Icons.Filled.Calculate, contentDescription = null, tint = Look.OnGold, modifier = Modifier.size(30.dp))
                    }
                }
            }
            // The ad slot: the real AdMob adaptive banner, kept clear of the controls above it.
            if (showAd) {
                Spacer(Modifier.height(10.dp))
                bannerSlot()
            }
        }
    }
}

@Composable
private fun BarButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(60.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.extra.cardAlt,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.extra.gold.copy(alpha = 0.5f)),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.extra.goldText)
            Text(label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
