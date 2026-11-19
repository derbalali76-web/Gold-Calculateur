package com.goldcalculateur.app.core

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Formats numbers for display. French uses a decimal comma; Arabic and English use a decimal
 * point with Latin digits (the convention used by Algerian jewellers and the app mock-ups).
 */
class NumberFormatter(languageTag: String) {

    private val comma = languageTag.lowercase().startsWith("fr")

    private fun localize(plain: String) = if (comma) plain.replace('.', ',') else plain

    /** Exactly [decimals] decimals, half-up. */
    fun fixed(value: BigDecimal, decimals: Int): String =
        localize(value.setScale(decimals, RoundingMode.HALF_UP).toPlainString())

    /** Up to [maxDecimals] decimals with trailing zeros removed (714.4, 200, 26.6667). */
    fun trimmed(value: BigDecimal, maxDecimals: Int): String {
        val rounded = value.setScale(maxDecimals, RoundingMode.HALF_UP)
        val stripped = if (rounded.signum() == 0) BigDecimal.ZERO else rounded.stripTrailingZeros()
        val plain = (if (stripped.scale() < 0) stripped.setScale(0) else stripped).toPlainString()
        return localize(plain)
    }
}
