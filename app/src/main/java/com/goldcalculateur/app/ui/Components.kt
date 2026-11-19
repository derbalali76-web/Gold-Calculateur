package com.goldcalculateur.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import java.math.BigDecimal
import com.goldcalculateur.app.core.InputParser
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldcalculateur.app.ui.theme.extra

/**
 * Everything is laid out in "mockup pixels": the mockup is 1024 px wide, and [LocalU] is dp-per-mockup-pixel
 * (screen width / 1024), so proportions match the design on every phone width.
 */
val LocalU = androidx.compose.runtime.compositionLocalOf { 0.4f }

@Composable
fun Int.px(): Dp = (this * LocalU.current).dp

@Composable
fun Int.spx(): androidx.compose.ui.unit.TextUnit = (this * LocalU.current).sp

/** Colours sampled from the mockup. */
object Look {
    val GoldGradient = Brush.verticalGradient(listOf(Color(0xFFFEF07A), Color(0xFFFCD23A), Color(0xFFF2B51C), Color(0xFFDDA20F)))
    val GoldTextGradient = Brush.verticalGradient(listOf(Color(0xFFFBE98A), Color(0xFFF9DF78), Color(0xFFFBCE3A)))
    val GoldBorder = Brush.verticalGradient(listOf(Color(0xFFE9C24B), Color(0xFFB8801A), Color(0xFFD9A93A)))
    val CardGradientDark = Brush.verticalGradient(listOf(Color(0xFF011422), Color(0xFF001D35), Color(0xFF00111F)))
    val FrameGradient = Brush.verticalGradient(listOf(Color(0xFF011121), Color(0xFF000C18), Color(0xFF000811)))
    val ScreenGradientDark = Brush.verticalGradient(listOf(Color(0xFF00152C), Color(0xFF000B1A), Color(0xFF01040B)))
    val UnselectedGradient = Brush.verticalGradient(listOf(Color(0xFF0F3149), Color(0xFF01182A)))
    val GridLine = Color(0xFF234D67)
    val TableHeader = Color(0xFF02182B)
    val TableCell = Color(0xFF010C19)
    val FieldBg = Color(0xFF010B19)
    val FieldBorder = Color(0xFF4F7695)
    val ResultCell = Color(0xFF001224)
    val GreenRowBg = Color(0xFF003614)
    val GreenRowBorder = Color(0xFF1FA23A)
    val OnGold = Color(0xFF1B1405)
    val Cream = Color(0xFFF5F1E6)
}

/** Navy card with a gold gradient outline (rounded like the mockup). */
@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val dark = MaterialTheme.extra.isDark
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (dark) Look.CardGradientDark else Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface)))
            .border(1.5.dp, Look.GoldBorder, shape)
            .padding(12.dp),
        content = content,
    )
}

/** Gold gradient pill button used for the primary actions. */
@Composable
fun GoldButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(18.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val outline = MaterialTheme.extra.isDark
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (selected) Look.GoldGradient else Brush.verticalGradient(listOf(Color(0xFF0E2A4D), Color(0xFF091B35))))
            .border(1.5.dp, if (selected) Brush.verticalGradient(listOf(Color(0xFFFFF3B0), Color(0xFFC9951A))) else SolidColor(Color(0xFF5B86A8)), shape)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Small up/down stepper (±1) shown next to the karat fields. */
@Composable
fun KaratStepper(value: String, onChange: (String) -> Unit, base: Int, modifier: Modifier = Modifier) {
    fun step(delta: Int) {
        val cur = InputParser.parse(value) ?: BigDecimal(base)
        val next = cur.add(BigDecimal(delta)).max(BigDecimal.ZERO).min(BigDecimal(1000))
        onChange(next.stripTrailingZeros().toPlainString())
    }
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .width(40.dp)
            .clip(shape)
            .background(Look.FieldBg)
            .border(1.dp, Look.FieldBorder, shape),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().weight(1f).clickable { step(1) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, tint = Color(0xFFF5C02B))
        }
        Box(Modifier.fillMaxWidth().weight(1f).clickable { step(-1) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Color(0xFFF5C02B))
        }
    }
}

/**
 * Compact decimal input used by every numeric cell. Numbers are always laid out left-to-right
 * (even in Arabic) and the keyboard is the decimal number pad.
 */
@Composable
fun CompactField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isError: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    height: Dp = 46.dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 19.sp,
    plain: Boolean = false,
    radius: Dp = 10.dp,
) {
    val shape = RoundedCornerShape(radius)
    val colors = MaterialTheme.colorScheme
    val borderColor = if (isError) colors.error else colors.outline
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            color = colors.onSurface,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            textDirection = TextDirection.Ltr,
        ),
        cursorBrush = SolidColor(MaterialTheme.extra.gold),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(if (plain) Color.Transparent else if (MaterialTheme.extra.isDark) Look.FieldBg else MaterialTheme.extra.fieldBackground)
            .then(
                if (plain && !isError) Modifier
                else Modifier.border(if (isError) 2.dp else 1.dp, if (plain) colors.error else if (MaterialTheme.extra.isDark && !isError) Look.FieldBorder else borderColor, shape)
            ),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        color = colors.onSurfaceVariant.copy(alpha = 0.45f),
                        fontSize = fontSize,
                        textAlign = TextAlign.Center,
                    )
                }
                inner()
            }
        },
    )
}
