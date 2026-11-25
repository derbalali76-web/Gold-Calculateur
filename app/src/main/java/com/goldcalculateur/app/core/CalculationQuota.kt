package com.goldcalculateur.app.core

import java.util.TimeZone

/** Calculations used today and bonus calculations earned by watching rewarded ads (reset each local day). */
data class QuotaState(val day: Long = 0L, val used: Int = 0, val bonus: Int = 0)

object QuotaClock {
    /** Local calendar day number (java.time is not available on API 24 without desugaring). */
    fun today(nowMillis: Long = System.currentTimeMillis()): Long =
        (nowMillis + TimeZone.getDefault().getOffset(nowMillis)) / 86_400_000L
}

/**
 * Pure rules for the "free calculations, then a rewarded ad" model. No SDK, fully unit-tested.
 *
 * The user is asked to watch an ad ONLY when: ads are not removed, the free + bonus calculations of
 * today are used up AND a rewarded ad is actually loaded. If no ad is available (offline, no fill,
 * no consent) the calculator keeps working: it never blocks on an ad.
 */
class CalculationQuota(
    private val freePerDay: Int,
    private val rewardPerAd: Int,
) {
    private fun normalize(s: QuotaState, today: Long) = if (s.day == today) s else QuotaState(today, 0, 0)

    fun remaining(s: QuotaState, today: Long): Int {
        val n = normalize(s, today)
        return (freePerDay + n.bonus - n.used).coerceAtLeast(0)
    }

    fun mustWatchAd(s: QuotaState, today: Long, adsRemoved: Boolean, rewardedReady: Boolean): Boolean =
        !adsRemoved && rewardedReady && remaining(s, today) == 0

    fun consume(s: QuotaState, today: Long): QuotaState = normalize(s, today).let { it.copy(used = it.used + 1) }

    fun grant(s: QuotaState, today: Long): QuotaState = normalize(s, today).let { it.copy(bonus = it.bonus + rewardPerAd) }
}

/** Decides whether an App Open ad may be shown when the app returns from the background. */
class AppOpenPolicy(
    private val minIdleMillis: Long,
    private val interstitialCooldownMillis: Long,
) {
    data class Inputs(
        /** 0 = the app has never been in the background in this process (cold start). */
        val backgroundedAtMillis: Long,
        val nowMillis: Long,
        val shownThisSession: Boolean,
        val adsRemoved: Boolean,
        val consentAllowsAds: Boolean,
        val adLoadedAndFresh: Boolean,
        val fullScreenAdShowing: Boolean,
        /** The user has typed weights/karats: never interrupt a calculation in progress. */
        val userMidOperation: Boolean,
        val lastInterstitialAtMillis: Long,
    )

    fun shouldShow(i: Inputs): Boolean {
        if (i.adsRemoved || !i.consentAllowsAds) return false
        if (!i.adLoadedAndFresh || i.fullScreenAdShowing || i.shownThisSession || i.userMidOperation) return false
        if (i.backgroundedAtMillis <= 0L) return false // cold start / first screen: never
        val idle = i.nowMillis - i.backgroundedAtMillis
        if (idle < minIdleMillis) return false // also covers a clock moved backwards
        if (i.lastInterstitialAtMillis > 0L && i.nowMillis - i.lastInterstitialAtMillis < interstitialCooldownMillis) return false
        return true
    }
}
