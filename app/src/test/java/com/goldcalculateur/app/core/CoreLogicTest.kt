package com.goldcalculateur.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class InputParserTest {
    @Test fun keepsDigitsAndOneSeparator() {
        assertEquals("12.5", InputParser.sanitize("12.5"))
        assertEquals("12,5", InputParser.sanitize("12,5"))
        assertEquals("12.57", InputParser.sanitize("12.5.7")) // 2nd separator dropped
        assertEquals("1250", InputParser.sanitize("1a2b5c0"))
    }

    @Test fun convertsArabicIndicDigitsAndSeparator() {
        assertEquals("748", InputParser.sanitize("٧٤٨"))
        assertEquals("12.5", InputParser.sanitize("١٢٫٥"))
        assertEquals("916", InputParser.sanitize("۹۱۶"))
    }

    @Test fun parsesBothSeparators() {
        assertEquals(BigDecimal("12.5"), InputParser.parse("12,5"))
        assertEquals(BigDecimal("12.5"), InputParser.parse("12.5"))
        assertEquals(BigDecimal("0.5"), InputParser.parse(".5"))
    }

    @Test fun rejectsBlankAndInvalid() {
        assertNull(InputParser.parse(""))
        assertNull(InputParser.parse("   "))
        assertNull(InputParser.parse("."))
        assertNull(InputParser.parse("abc"))
        assertEquals("5", InputParser.sanitize("-5")) // minus sign is never accepted
    }

    @Test fun limitsLength() {
        assertEquals(14, InputParser.sanitize("1".repeat(40)).length)
    }
}

class NumberFormatterTest {
    @Test fun decimalPointForArabicAndEnglish() {
        val v = BigDecimal("26.66666666667")
        assertEquals("26.6667", NumberFormatter("ar").fixed(v, 4))
        assertEquals("26.667", NumberFormatter("en").fixed(v, 3))
    }

    @Test fun decimalCommaForFrench() {
        assertEquals("26,67", NumberFormatter("fr").fixed(BigDecimal("26.666"), 2))
        assertEquals("714,4", NumberFormatter("fr-FR").trimmed(BigDecimal("714.4000"), 3))
    }

    @Test fun trimmedRemovesZerosWithoutScientificNotation() {
        val f = NumberFormatter("en")
        assertEquals("200", f.trimmed(BigDecimal("200.000"), 4))
        assertEquals("100", f.trimmed(BigDecimal("100"), 3))
        assertEquals("0", f.trimmed(BigDecimal("0.0000"), 3))
        assertEquals("714.4", f.trimmed(BigDecimal("714.4"), 3))
        assertEquals("1000", f.trimmed(BigDecimal("1000"), 3))
    }

    @Test fun roundsHalfUp() {
        assertEquals("0.67", NumberFormatter("en").fixed(BigDecimal("0.665"), 2))
    }
}

class WorkStateCodecTest {
    @Test fun roundTrip() {
        val s = WorkState(
            rows = listOf(IngotRow(0, "100", "700"), IngotRow(1, "60,5", "720"), IngotRow(2, "40", "")),
            target = "748", mode = CalcMode.CUT_REFINE, addedKarat = "917",
        )
        val back = WorkStateCodec.decode(WorkStateCodec.encode(s))!!
        assertEquals(s.rows.map { it.weight to it.karat }, back.rows.map { it.weight to it.karat })
        assertEquals("748", back.target)
        assertEquals(CalcMode.CUT_REFINE, back.mode)
        assertEquals("917", back.addedKarat)
    }

    @Test fun emptyRowsBecomeOneBlankRow() {
        val back = WorkStateCodec.decode("v1|ADD_INGOT|||")!!
        assertEquals(1, back.rows.size)
        assertTrue(back.rows[0].isBlank)
        assertEquals("1000", back.addedKarat) // default restored
    }

    @Test fun garbageIsRejected() {
        assertNull(WorkStateCodec.decode(null))
        assertNull(WorkStateCodec.decode(""))
        assertNull(WorkStateCodec.decode("hello"))
        assertNull(WorkStateCodec.decode("v9|ADD_INGOT|1|1|1:1"))
        assertNull(WorkStateCodec.decode("v1|NOPE|1|1|1:1"))
    }

    @Test fun separatorsCannotBeInjected() {
        val s = WorkState(listOf(IngotRow(0, "1|2;3:4", "5")), "7|8", CalcMode.ADD_INGOT, "1000")
        val back = WorkStateCodec.decode(WorkStateCodec.encode(s))!!
        assertEquals(1, back.rows.size)
        assertEquals("1234", back.rows[0].weight)
    }
}

class InterstitialPolicyTest {
    private val policy = InterstitialPolicy(everyNCalculations = 3, cooldownMillis = 180_000)
    private val ok = InterstitialPolicy.Inputs(
        completedSinceLastAd = 3, lastShownAtMillis = 0, nowMillis = 1_000_000, adsRemoved = false,
        consentAllowsAds = true, adLoaded = true, appInForeground = true, fullScreenAdShowing = false,
    )

    @Test fun showsAfterThreeCalculations() = assertTrue(policy.shouldShow(ok))
    @Test fun notBeforeThreeCalculations() = assertFalse(policy.shouldShow(ok.copy(completedSinceLastAd = 2)))
    @Test fun neverAfterPurchase() = assertFalse(policy.shouldShow(ok.copy(adsRemoved = true)))
    @Test fun neverWithoutConsent() = assertFalse(policy.shouldShow(ok.copy(consentAllowsAds = false)))
    @Test fun skippedWhenAdFailedToLoad() = assertFalse(policy.shouldShow(ok.copy(adLoaded = false)))
    @Test fun neverInBackground() = assertFalse(policy.shouldShow(ok.copy(appInForeground = false)))
    @Test fun neverBackToBack() = assertFalse(policy.shouldShow(ok.copy(fullScreenAdShowing = true)))

    @Test fun cooldownIsRespected() {
        assertFalse(policy.shouldShow(ok.copy(lastShownAtMillis = 900_000))) // 100 s ago
        assertTrue(policy.shouldShow(ok.copy(lastShownAtMillis = 819_999)))  // > 180 s ago
        assertFalse(policy.shouldShow(ok.copy(lastShownAtMillis = 820_001))) // < 180 s ago
    }

    @Test fun clockMovedBackwardsDoesNotShow() =
        assertFalse(policy.shouldShow(ok.copy(lastShownAtMillis = 2_000_000, nowMillis = 1_000_000)))

    @Test fun frequencyAndCooldownAreConfigurable() {
        val p = InterstitialPolicy(everyNCalculations = 5, cooldownMillis = 10_000)
        assertFalse(p.shouldShow(ok.copy(completedSinceLastAd = 4)))
        assertTrue(p.shouldShow(ok.copy(completedSinceLastAd = 5, lastShownAtMillis = 980_000)))
    }
}

class EntitlementLogicTest {
    private val id = "remove_ads_lifetime"
    private fun p(state: PurchaseStateKind, ack: Boolean, product: String = id, token: String = "t") =
        PurchaseSnapshot(listOf(product), state, ack, token)

    @Test fun purchasedGrantsEntitlementAndRequestsAcknowledgement() {
        val d = EntitlementLogic.decide(id, listOf(p(PurchaseStateKind.PURCHASED, ack = false, token = "abc")))
        assertTrue(d.entitled)
        assertEquals(listOf("abc"), d.tokensToAcknowledge)
    }

    @Test fun acknowledgedPurchaseIsNotAcknowledgedTwice() {
        val d = EntitlementLogic.decide(id, listOf(p(PurchaseStateKind.PURCHASED, ack = true)))
        assertTrue(d.entitled)
        assertTrue(d.tokensToAcknowledge.isEmpty())
    }

    @Test fun pendingPurchaseIsNotASuccess() {
        val d = EntitlementLogic.decide(id, listOf(p(PurchaseStateKind.PENDING, ack = false)))
        assertFalse(d.entitled)
        assertTrue(d.pending)
        assertTrue(d.tokensToAcknowledge.isEmpty())
    }

    @Test fun noPurchasesMeansNoEntitlement() {
        val d = EntitlementLogic.decide(id, emptyList())
        assertFalse(d.entitled)
        assertFalse(d.pending)
    }

    @Test fun otherProductsAreIgnored() {
        val d = EntitlementLogic.decide(id, listOf(p(PurchaseStateKind.PURCHASED, true, product = "something_else")))
        assertFalse(d.entitled)
    }

    @Test fun responseCodesAreClassified() {
        assertEquals(BillingFailure.NONE, EntitlementLogic.classify(0))
        assertEquals(BillingFailure.CANCELLED, EntitlementLogic.classify(1))
        assertEquals(BillingFailure.ALREADY_OWNED, EntitlementLogic.classify(7))
        assertEquals(BillingFailure.NETWORK, EntitlementLogic.classify(12))
        assertEquals(BillingFailure.NETWORK, EntitlementLogic.classify(-1))
        assertEquals(BillingFailure.UNAVAILABLE, EntitlementLogic.classify(3))
        assertEquals(BillingFailure.OTHER, EntitlementLogic.classify(6))
    }
}

class LanguageMappingTest {
    @Test fun mapsTags() {
        assertEquals(AppLanguage.ARABIC, AppLanguage.fromTag("ar"))
        assertEquals(AppLanguage.ARABIC, AppLanguage.fromTag("ar-DZ"))
        assertEquals(AppLanguage.FRENCH, AppLanguage.fromTag("fr_FR"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en-US"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("de"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag(null))
    }

    @Test fun onlyArabicIsRtl() {
        assertTrue(AppLanguage.ARABIC.isRtl)
        assertFalse(AppLanguage.FRENCH.isRtl)
        assertFalse(AppLanguage.ENGLISH.isRtl)
    }
}
