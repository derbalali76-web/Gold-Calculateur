package com.goldcalculateur.app.ads

import android.app.Activity
import android.app.Application
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.goldcalculateur.app.BuildConfig
import com.goldcalculateur.app.core.InterstitialPolicy
import com.goldcalculateur.app.data.SettingsRepository
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns everything ad-related: consent (UMP), SDK initialisation, the interstitial lifecycle.
 * Only two formats exist: adaptive banner (see [AdaptiveBanner]) and interstitial.
 * No rewarded, app-open or native ads.
 *
 * The interstitial is shown ONLY from [onNaturalTransition], which the UI calls when the user
 * navigates back to the calculator from the History or Settings screens.
 */
class AdsManager(
    private val app: Application,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val policy = InterstitialPolicy(
        everyNCalculations = BuildConfig.INTERSTITIAL_EVERY_N_CALCS,
        cooldownMillis = BuildConfig.INTERSTITIAL_COOLDOWN_SECONDS * 1000L,
    )

    private val _canRequestAds = MutableStateFlow(false)
    /** True once consent allows ad requests AND the SDK is initialised. */
    val canRequestAds: StateFlow<Boolean> = _canRequestAds

    private val _privacyOptionsRequired = MutableStateFlow(false)
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired

    @Volatile private var adsRemoved = false
    private var interstitial: InterstitialAd? = null
    private var loading = false
    private var fullScreenShowing = false
    private val sdkInitStarted = AtomicBoolean(false)
    private var consentGathered = false

    /** Called whenever the entitlement changes. After purchase: stop loading and showing ads. */
    fun setAdsRemoved(removed: Boolean) {
        adsRemoved = removed
        if (removed) {
            interstitial = null
            _canRequestAds.value = false
        } else if (consentGathered) {
            refreshConsentState(UserMessagingPlatform.getConsentInformation(app))
        }
    }

    /** Gathers consent (EEA/UK etc.) through Google's UMP, then initialises the SDK. */
    fun gatherConsent(activity: Activity) {
        if (adsRemoved || consentGathered) return
        consentGathered = true
        val info = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder().build()
        info.requestConsentInfoUpdate(
            activity, params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    if (error != null) Log.w(TAG, "Consent form: ${error.errorCode}")
                    refreshConsentState(info)
                }
            },
            { error ->
                Log.w(TAG, "Consent info update failed: ${error.errorCode}")
                refreshConsentState(info) // a previous session may already allow requests
            },
        )
        // Consent obtained in a previous session: ads may be requested right away.
        refreshConsentState(info)
    }

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) Log.w(TAG, "Privacy options: ${error.errorCode}")
            refreshConsentState(UserMessagingPlatform.getConsentInformation(activity))
        }
    }

    private fun refreshConsentState(info: ConsentInformation) {
        _privacyOptionsRequired.value =
            info.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        if (!adsRemoved && info.canRequestAds()) initializeSdk()
    }

    private fun initializeSdk() {
        if (sdkInitStarted.getAndSet(true)) {
            _canRequestAds.value = !adsRemoved
            if (!adsRemoved) preloadInterstitial()
            return
        }
        scope.launch(Dispatchers.IO) {
            MobileAds.initialize(app) { }
            scope.launch(Dispatchers.Main) {
                _canRequestAds.value = !adsRemoved
                preloadInterstitial()
            }
        }
    }

    private fun preloadInterstitial() {
        if (adsRemoved || !_canRequestAds.value || interstitial != null || loading) return
        loading = true
        InterstitialAd.load(
            app, BuildConfig.ADMOB_INTERSTITIAL_UNIT_ID, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loading = false
                    if (adsRemoved) return
                    interstitial = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    // Load failure never blocks the app. We simply try again at the next opportunity.
                    loading = false
                    interstitial = null
                    Log.w(TAG, "Interstitial failed to load: ${error.code}")
                }
            },
        )
    }

    /**
     * Call ONLY on a natural, user-initiated navigation transition (e.g. back from History or
     * Settings). Never at launch or exit, never while typing, never on "Calculate".
     */
    fun onNaturalTransition(activity: Activity) {
        scope.launch(Dispatchers.Main) {
            val counters = settings.adCounters.first()
            val now = System.currentTimeMillis()
            val inForeground = (activity as? LifecycleOwner)?.lifecycle?.currentState
                ?.isAtLeast(Lifecycle.State.RESUMED) ?: true
            val ad = interstitial
            val show = policy.shouldShow(
                InterstitialPolicy.Inputs(
                    completedSinceLastAd = counters.completedSinceLastAd,
                    lastShownAtMillis = counters.lastInterstitialAtMillis,
                    nowMillis = now,
                    adsRemoved = adsRemoved,
                    consentAllowsAds = _canRequestAds.value,
                    adLoaded = ad != null,
                    appInForeground = inForeground,
                    fullScreenAdShowing = fullScreenShowing,
                )
            )
            if (!show || ad == null) {
                // Not eligible (or nothing loaded): skip silently; calculations are never affected.
                if (counters.completedSinceLastAd >= BuildConfig.INTERSTITIAL_EVERY_N_CALCS) preloadInterstitial()
                return@launch
            }
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    fullScreenShowing = true
                    scope.launch { settings.markInterstitialShown(System.currentTimeMillis()) }
                }

                override fun onAdDismissedFullScreenContent() {
                    fullScreenShowing = false
                    interstitial = null
                    preloadInterstitial()
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    fullScreenShowing = false
                    interstitial = null
                    Log.w(TAG, "Interstitial failed to show: ${error.code}")
                    preloadInterstitial()
                }
            }
            interstitial = null
            ad.show(activity)
        }
    }

    private companion object {
        const val TAG = "AdsManager"
    }
}
