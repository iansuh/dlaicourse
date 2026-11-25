package app.numbaseball.ads

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.numbaseball.ui.common.rememberWindowHeightDp
import app.numbaseball.ui.theme.NbColors
import app.numbaseball.ui.theme.NbLayout
import app.numbaseball.ui.theme.NbMotion
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

/** Test tag of the banner container (QA test hook): tests assert it is absent on screens that must not show ads. */
const val AD_BANNER_TAG = "adBanner"

/** Disabled unless the activity provides the real controller, so previews, tests and screenshots never show ads. */
val LocalAdsController = staticCompositionLocalOf<AdsController> { DisabledAdsController }

/**
 * Screenshot hook: a stand-in creative of the given height (dp) drawn inside the real band, so reference renders show
 * the band where design/shots show it. Null (default) everywhere except screenshot tests.
 */
val LocalAdPreview = staticCompositionLocalOf<(@Composable () -> Unit)?> { null }

/**
 * Anchored adaptive banner for calm screens only (home, results, profile; never during a match, in a filling lobby or in
 * chat). Takes no space until an ad has loaded, and is hidden in windows lower than the design's 480dp threshold.
 */
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    val preview = LocalAdPreview.current
    if (preview != null) {
        if (rememberWindowHeightDp() < NbLayout.bannerHideWindowHeightBelow) return
        AdBand(modifier.testTag(AD_BANNER_TAG), height = 50.dp) { preview() }
        return
    }
    val ads = LocalAdsController.current
    val adUnitId = ads.bannerAdUnitId ?: return
    val canRequestAds by ads.canRequestAds.collectAsStateWithLifecycle()
    val windowHeightDp = rememberWindowHeightDp()
    if (!canRequestAds || windowHeightDp < NbLayout.bannerHideWindowHeightBelow || LocalInspectionMode.current) return
    BoxWithConstraints(modifier.fillMaxWidth().testTag(AD_BANNER_TAG)) {
        val widthDp = maxWidth.value.toInt()
        if (widthDp < 320) return@BoxWithConstraints
        key(widthDp) { AnchoredBanner(adUnitId, widthDp) }
    }
}

@Composable
private fun AnchoredBanner(adUnitId: String, widthDp: Int) {
    val context = LocalContext.current
    var loaded by remember { mutableStateOf(false) }
    val adSize = remember(widthDp) { AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, widthDp) }
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
    // AdBand (DESIGN_SYSTEM 13): 1 dp `line` top edge + `surface1` band of the ad's height; 0 dp until an ad loads, then
    // it expands in 240 ms (decelerate). The creative is centred when narrower than the band.
    val bandHeight by animateDpAsState(if (loaded) adSize.height.dp else 0.dp, tween(NbMotion.medium, easing = LinearOutSlowInEasing), label = "adBand")
    AdBand(Modifier, bandHeight, visible = bandHeight > 0.dp) { AndroidView(factory = { adView }, modifier = Modifier.height(adSize.height.dp)) }
}

/** 1 dp `line` top edge + `surface1` band of [height]; the creative is centred. Nothing (0 dp) while not [visible]. */
@Composable
private fun AdBand(modifier: Modifier, height: Dp, visible: Boolean = true, creative: @Composable () -> Unit) {
    if (!visible) return
    Column(modifier.fillMaxWidth().height(height + 1.dp).clipToBounds()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(NbColors.line))
        Box(Modifier.fillMaxWidth().weight(1f).background(NbColors.surface1), contentAlignment = Alignment.Center) { creative() }
    }
}
