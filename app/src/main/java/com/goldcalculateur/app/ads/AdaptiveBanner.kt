package com.goldcalculateur.app.ads

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import kotlinx.coroutines.delay

private const val RETRY_AFTER_FAILURE_MS = 60_000L

/**
 * The real AdMob ADAPTIVE BANNER. It replaces the old "advertising space" placeholder:
 * the area below the controls is exactly the ad slot; no fake ad artwork is drawn.
 *
 *  - height = the adaptive size computed from the available width (space is reserved while loading)
 *  - if the request fails the slot collapses to zero height (no big empty area) and retries after 60 s
 *  - paused/resumed with the lifecycle, destroyed on dispose
 */
@Composable
fun AdaptiveBanner(unitId: String, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val context = LocalContext.current
        val widthDp = maxWidth.value.toInt()
        val adSize = remember(widthDp) {
            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
        }
        var failed by remember(widthDp) { mutableStateOf(false) }

        LaunchedEffect(failed) {
            if (failed) {
                delay(RETRY_AFTER_FAILURE_MS)
                failed = false
            }
        }

        if (!failed) {
            val adView = remember(unitId, widthDp) {
                AdView(context).apply {
                    setAdSize(adSize)
                    adUnitId = unitId
                    adListener = object : AdListener() {
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            failed = true
                        }
                    }
                    loadAd(AdRequest.Builder().build())
                }
            }
            DisposableEffect(adView) { onDispose { adView.destroy() } }
            LifecycleResumeEffect(adView) {
                adView.resume()
                onPauseOrDispose { adView.pause() }
            }
            AndroidView(
                factory = { adView },
                modifier = Modifier.fillMaxWidth().height(adSize.height.dp),
            )
        }
    }
}
