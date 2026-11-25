package com.goldcalculateur.app.ads

import android.app.Activity
import android.app.Application
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.goldcalculateur.app.BuildConfig
import com.goldcalculateur.app.core.AppOpenPolicy
import com.goldcalculateur.app.core.InterstitialPolicy
import com.goldcalculateur.app.data.SettingsRepository
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
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

    private val appOpenPolicy = AppOpenPolicy(
        minIdleMillis = BuildConfig.APP_OPEN_MIN_IDLE_MINUTES * 60_000L,
        interstitialCooldownMillis = BuildConfig.INTERSTITIAL_COOLDOWN_SECONDS * 1000L,
    )

    private val _canRequestAds = MutableStateFlow(false)
    /** True once consent allows ad requests AND the SDK is initialised. */
    val canRequestAds: StateFlow<Boolean> = _canRequestAds

    private val _privacyOptionsRequired = MutableStateFlow(false)
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired

    private val _rewardedReady = MutableStateFlow(false)
    /** True when a rewarded ad is loaded and can be offered. The calculator never waits for it. */
    val rewardedReady: StateFlow<Boolean> = _rewardedReady

    @Volatile private var adsRemoved = false
    private var rewarded: RewardedAd? = null
    private var rewardedLoading = false
    private var appOpen: AppOpenAd? = null
    private var appOpenLoadedAt = 0L
    private var appOpenLoading = false
    private var appOpenShownThisSession = false
    private var lastAppOpenShownAt = 0L
    private var backgroundedAt = 0L
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
            rewarded = null
            appOpen = null
            _rewardedReady.value = false
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
            if (!adsRemoved) preloadAll()
            return
        }
        scope.launch(Dispatchers.IO) {
            MobileAds.initialize(app) { }
            scope.launch(Dispatchers.Main) {
                _canRequestAds.value = !adsRemoved
                preloadAll()
            }
        }
    }

    /** Everything is loaded in the background ahead of time; failures are silent and retried later. */
    private fun preloadAll() {
        preloadInterstitial()
        preloadRewarded()
        preloadAppOpen()
    }

    private fun preloadRewarded() {
        val unitId = BuildConfig.ADMOB_REWARDED_UNIT_ID
        if (unitId.isBlank() || adsRemoved || !_canRequestAds.value || rewarded != null || rewardedLoading) return
        rewardedLoading = true
        RewardedAd.load(app, unitId, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                rewardedLoading = false
                if (adsRemoved) return
                rewarded = ad
                _rewardedReady.value = true
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                rewardedLoading = false
                rewarded = null
                _rewardedReady.value = false
                Log.w(TAG, "Rewarded failed to load: ${error.code}")
            }
        })
    }

    private fun preloadAppOpen() {
        val unitId = BuildConfig.ADMOB_APP_OPEN_UNIT_ID
        if (unitId.isBlank() || adsRemoved || !_canRequestAds.value || appOpenLoading) return
        if (appOpen != null && !appOpenExpired()) return
        appOpenLoading = true
        AppOpenAd.load(app, unitId, AdRequest.Builder().build(), object : AppOpenAd.AppOpenAdLoadCallback() {
            override fun onAdLoaded(ad: AppOpenAd) {
                appOpenLoading = false
                if (adsRemoved) return
                appOpen = ad
                appOpenLoadedAt = System.currentTimeMillis()
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                appOpenLoading = false
                appOpen = null
                Log.w(TAG, "App open failed to load: ${error.code}")
            }
        })
    }

    /** App Open ads expire 4 hours after loading. */
    private fun appOpenExpired() = System.currentTimeMillis() - appOpenLoadedAt > 4 * 3_600_000L

    fun onAppBackgrounded() {
        backgroundedAt = System.currentTimeMillis()
    }

    /**
     * Called when the app comes to the foreground. Shows the App Open ad only after at least the
     * configured idle time in the background, once per session, never on a cold start and never while
     * the user is in the middle of an operation.
     */
    fun onAppForegrounded(activity: Activity, userMidOperation: Boolean) {
        scope.launch(Dispatchers.Main) {
            val now = System.currentTimeMillis()
            val ad = appOpen
            val show = appOpenPolicy.shouldShow(
                AppOpenPolicy.Inputs(
                    backgroundedAtMillis = backgroundedAt,
                    nowMillis = now,
                    shownThisSession = appOpenShownThisSession,
                    adsRemoved = adsRemoved,
                    consentAllowsAds = _canRequestAds.value,
                    adLoadedAndFresh = ad != null && !appOpenExpired(),
                    fullScreenAdShowing = fullScreenShowing,
                    userMidOperation = userMidOperation,
                    lastInterstitialAtMillis = settings.adCounters.first().lastInterstitialAtMillis,
                )
            )
            if (!show || ad == null) {
                if (backgroundedAt > 0L) preloadAppOpen() // refresh quietly for next time
                return@launch
            }
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    fullScreenShowing = true
                    appOpenShownThisSession = true
                    lastAppOpenShownAt = System.currentTimeMillis()
                }

                override fun onAdDismissedFullScreenContent() {
                    fullScreenShowing = false
                    appOpen = null
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    fullScreenShowing = false
                    appOpen = null
                    Log.w(TAG, "App open failed to show: ${error.code}")
                }
            }
            appOpen = null
            ad.show(activity)
        }
    }

    /**
     * Offers the rewarded ad. [onResult] is called exactly once with true when the calculation may go
     * ahead (the reward was earned, or no ad could be shown) and false when the user closed it early.
     * An ad problem can therefore never block the calculator.
     */
    fun showRewarded(activity: Activity, onRewarded: () -> Unit, onResult: (Boolean) -> Unit) {
        val ad = rewarded
        if (ad == null || adsRemoved || fullScreenShowing) {
            onResult(true)
            return
        }
        var earned = false
        var reported = false
        fun finish(ok: Boolean) { if (!reported) { reported = true; onResult(ok) } }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() { fullScreenShowing = true }

            override fun onAdDismissedFullScreenContent() {
                fullScreenShowing = false
                rewarded = null
                _rewardedReady.value = false
                preloadRewarded()
                finish(earned)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                fullScreenShowing = false
                rewarded = null
                _rewardedReady.value = false
                Log.w(TAG, "Rewarded failed to show: ${error.code}")
                preloadRewarded()
                finish(true)
            }
        }
        rewarded = null
        _rewardedReady.value = false
        ad.show(activity) {
            earned = true
            onRewarded()
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
                    fullScreenAdShowing = fullScreenShowing ||
                        (lastAppOpenShownAt > 0L && now - lastAppOpenShownAt < BuildConfig.INTERSTITIAL_COOLDOWN_SECONDS * 1000L),
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
