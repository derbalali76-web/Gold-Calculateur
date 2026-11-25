package com.goldcalculateur.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculationQuotaTest {
    private val q = CalculationQuota(freePerDay = 3, rewardPerAd = 3)
    private val day = 100L

    @Test fun threeFreeCalculationsThenAdIsRequired() {
        var s = QuotaState()
        repeat(3) {
            assertFalse(q.mustWatchAd(s, day, adsRemoved = false, rewardedReady = true))
            s = q.consume(s, day)
        }
        assertTrue(q.mustWatchAd(s, day, adsRemoved = false, rewardedReady = true))
    }

    @Test fun neverBlocksWhenNoAdIsAvailable() {
        val s = QuotaState(day, used = 50, bonus = 0)
        assertFalse(q.mustWatchAd(s, day, adsRemoved = false, rewardedReady = false))
    }

    @Test fun buyersAreNeverBlocked() {
        val s = QuotaState(day, used = 50, bonus = 0)
        assertFalse(q.mustWatchAd(s, day, adsRemoved = true, rewardedReady = true))
    }

    @Test fun rewardGivesMoreCalculations() {
        var s = QuotaState(day, used = 3, bonus = 0)
        s = q.grant(s, day)
        assertEquals(3, q.remaining(s, day))
        repeat(3) { s = q.consume(s, day) }
        assertTrue(q.mustWatchAd(s, day, false, true))
    }

    @Test fun quotaResetsOnANewDay() {
        val s = QuotaState(day, used = 9, bonus = 6)
        assertEquals(3, q.remaining(s, day + 1))
        assertEquals(QuotaState(day + 1, 1, 0), q.consume(s, day + 1))
    }
}

class AppOpenPolicyTest {
    private val policy = AppOpenPolicy(minIdleMillis = 15 * 60_000L, interstitialCooldownMillis = 180_000L)
    private val now = 10_000_000L
    private val ok = AppOpenPolicy.Inputs(
        backgroundedAtMillis = now - 16 * 60_000L, nowMillis = now, shownThisSession = false,
        adsRemoved = false, consentAllowsAds = true, adLoadedAndFresh = true, fullScreenAdShowing = false,
        userMidOperation = false, lastInterstitialAtMillis = 0L,
    )

    @Test fun showsAfter15MinutesIdle() = assertTrue(policy.shouldShow(ok))
    @Test fun notBefore15Minutes() = assertFalse(policy.shouldShow(ok.copy(backgroundedAtMillis = now - 14 * 60_000L)))
    @Test fun neverOnColdStart() = assertFalse(policy.shouldShow(ok.copy(backgroundedAtMillis = 0L)))
    @Test fun onlyOncePerSession() = assertFalse(policy.shouldShow(ok.copy(shownThisSession = true)))
    @Test fun neverMidOperation() = assertFalse(policy.shouldShow(ok.copy(userMidOperation = true)))
    @Test fun neverForBuyersOrWithoutConsent() {
        assertFalse(policy.shouldShow(ok.copy(adsRemoved = true)))
        assertFalse(policy.shouldShow(ok.copy(consentAllowsAds = false)))
    }
    @Test fun neverWithoutALoadedFreshAd() = assertFalse(policy.shouldShow(ok.copy(adLoadedAndFresh = false)))
    @Test fun notRightAfterAnInterstitial() =
        assertFalse(policy.shouldShow(ok.copy(lastInterstitialAtMillis = now - 60_000L)))
    @Test fun clockMovedBackwardsDoesNotShow() =
        assertFalse(policy.shouldShow(ok.copy(backgroundedAtMillis = now + 1_000L)))
}
