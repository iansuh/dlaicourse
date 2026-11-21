package app.numbaseball.ads

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.numbaseball.ui.common.rememberWindowHeightDp
import app.numbaseball.ui.theme.NbLayout
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

/** Disabled unless the activity provides the real controller, so previews, tests and screenshots never show ads. */
val LocalAdsController = staticCompositionLocalOf<AdsController> { DisabledAdsController }

/**
 * Anchored adaptive banner for calm screens only (home, results, profile; never during a match, in a filling lobby or in
 * chat). Takes no space until an ad has loaded, and is hidden in windows lower than the design's 480dp threshold.
 */
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    val ads = LocalAdsController.current
    val adUnitId = ads.bannerAdUnitId ?: return
    val canRequestAds by ads.canRequestAds.collectAsStateWithLifecycle()
    val windowHeightDp = rememberWindowHeightDp()
    if (!canRequestAds || windowHeightDp < NbLayout.bannerHideWindowHeightBelow || LocalInspectionMode.current) return
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widthDp = maxWidth.value.toInt()
        key(widthDp) { AnchoredBanner(adUnitId, widthDp) }
    }
}

@Composable
private fun AnchoredBanner(adUnitId: String, widthDp: Int) {
    val context = LocalContext.current
    var loaded by remember { mutableStateOf(false) }
    val adSize = remember(widthDp) { AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp) }
    val adView = remember(adUnitId, adSize) {
        AdView(context).apply {
            setAdSize(adSize)
            setAdUnitId(adUnitId)
            setAdListener(
                object : AdListener() {
                    override fun onAdLoaded() {
                        loaded = true
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        loaded = false
                    }
                },
            )
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
        modifier = Modifier.fillMaxWidth().height(if (loaded) adSize.height.dp else 0.dp),
    )
}
