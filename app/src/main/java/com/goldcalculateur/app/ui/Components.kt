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

/** Look of the mockup: deep-blue gradients, gold gradients, fine blue grid lines. */
object Look {
    val GoldGradient = Brush.verticalGradient(listOf(Color(0xFFFFE87A), Color(0xFFF7C531), Color(0xFFDDA20F)))
    val GoldTextGradient = Brush.verticalGradient(listOf(Color(0xFFFFF0A0), Color(0xFFF5C02B), Color(0xFFD9A00F)))
    val GoldBorder = Brush.verticalGradient(listOf(Color(0xFFF3CB52), Color(0xFFB88416), Color(0xFFE8B93A)))
    val CardGradientDark = Brush.verticalGradient(listOf(Color(0xFF0C2347), Color(0xFF081832)))
    val ScreenGradientDark = Brush.verticalGradient(listOf(Color(0xFF071A38), Color(0xFF040E22)))
    val GridLine = Color(0xFF2E536A)
    val TableHeader = Color(0xFF0A2848)
    val TableCell = Color(0xFF0B2038)
    val OnGold = Color(0xFF1B1405)
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
            .background(Look.TableCell)
            .border(1.dp, Look.GridLine, shape),
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
    fontSize: Int = 19,
    plain: Boolean = false,
) {
    val shape = RoundedCornerShape(10.dp)
    val colors = MaterialTheme.colorScheme
    val borderColor = if (isError) colors.error else colors.outline
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            color = colors.onSurface,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            textDirection = TextDirection.Ltr,
        ),
        cursorBrush = SolidColor(MaterialTheme.extra.gold),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(if (plain) Color.Transparent else MaterialTheme.extra.fieldBackground)
            .then(
                if (plain && !isError) Modifier
                else Modifier.border(if (isError) 2.dp else 1.dp, if (plain) colors.error else borderColor, shape)
            ),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        color = colors.onSurfaceVariant.copy(alpha = 0.45f),
                        fontSize = fontSize.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                inner()
            }
        },
    )
}
