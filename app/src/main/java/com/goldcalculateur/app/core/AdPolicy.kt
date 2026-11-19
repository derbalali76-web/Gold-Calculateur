package com.goldcalculateur.app.core

/**
 * Pure decision logic for interstitials (no SDK, fully unit-tested).
 * It is only ever consulted at a natural navigation transition, never at launch/exit,
 * never while typing and never when the user presses "Calculate".
 */
class InterstitialPolicy(
    private val everyNCalculations: Int,
    private val cooldownMillis: Long,
) {
    data class Inputs(
        val completedSinceLastAd: Int,
        val lastShownAtMillis: Long,
        val nowMillis: Long,
        val adsRemoved: Boolean,
        val consentAllowsAds: Boolean,
        val adLoaded: Boolean,
        val appInForeground: Boolean,
        val fullScreenAdShowing: Boolean,
    )

    fun shouldShow(i: Inputs): Boolean {
        if (i.adsRemoved || !i.consentAllowsAds) return false
        if (!i.adLoaded || !i.appInForeground || i.fullScreenAdShowing) return false
        if (i.completedSinceLastAd < everyNCalculations) return false
        if (i.lastShownAtMillis > 0L) {
            val elapsed = i.nowMillis - i.lastShownAtMillis
            if (elapsed < cooldownMillis) return false // also covers a clock moved backwards
        }
        return true
    }
}
