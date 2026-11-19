package com.goldcalculateur.app.core

import java.math.BigDecimal

/** Keyboard input handling shared by every numeric field. Pure Kotlin (unit-testable). */
object InputParser {

    private const val MAX_LENGTH = 14

    private fun latinDigit(ch: Char): Char = when (ch) {
        in '٠'..'٩' -> '0' + (ch - '٠') // Arabic-Indic digits
        in '۰'..'۹' -> '0' + (ch - '۰') // Eastern Arabic-Indic digits
        else -> ch
    }

    /**
     * Keeps only digits and a single decimal separator ('.' or ','). Arabic digits and the Arabic
     * decimal separator are converted so that any keyboard works.
     */
    fun sanitize(raw: String): String {
        val sb = StringBuilder()
        var separatorSeen = false
        for (original in raw) {
            val c = latinDigit(original)
            when {
                c in '0'..'9' -> sb.append(c)
                (c == '.' || c == ',' || c == '٫') && !separatorSeen -> {
                    separatorSeen = true
                    sb.append(if (c == '٫') '.' else c)
                }
            }
        }
        return sb.toString().take(MAX_LENGTH)
    }

    /** Parses a sanitized field. Returns null for blank/invalid text. */
    fun parse(raw: String): BigDecimal? {
        val t = sanitize(raw).replace(',', '.')
        if (t.isEmpty() || t == ".") return null
        return t.toBigDecimalOrNull()
    }
}
