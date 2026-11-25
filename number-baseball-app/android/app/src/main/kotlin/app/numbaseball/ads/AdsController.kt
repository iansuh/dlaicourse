package app.numbaseball.ads

import android.app.Activity
import app.numbaseball.core.protocol.EndReason
import app.numbaseball.data.age.AdAgeTreatment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Interstitials between a finished match and its results (06 §3). The decision is [InterstitialRules.decide]; this keeps
 * the cap's memory and the loaded ad. Main thread only.
 */
interface InterstitialController {
    /** Every match end (vs computer, online duel, whole team match, practice, review room); only eligible ones count. */
    fun onMatchFinished(kind: FinishedMatchKind, endReason: EndReason?, serverRestart: Boolean = false)

    val finishedSinceLastAd: Int
    val sinceLastAdMillis: Long
    val isLoaded: Boolean

    /** Shows the loaded ad (muted) and calls [onClosed] when it is dismissed or fails to show. */
    fun show(activity: Activity, onClosed: () -> Unit)
}

/** No interstitial ever (tests, previews, ads disabled). */
object NoInterstitials : InterstitialController {
    override fun onMatchFinished(kind: FinishedMatchKind, endReason: EndReason?, serverRestart: Boolean) = Unit
    override val finishedSinceLastAd: Int = 0
    override val sinceLastAdMillis: Long = 0
    override val isLoaded: Boolean = false
    override fun show(activity: Activity, onClosed: () -> Unit) = onClosed()
}

/**
 * Ads per README "Monetization": banners only on calm screens, interstitials between a finished match (vs computer or
 * online) and its results, muted, consent through UMP; none of it once `remove_ads` is owned (the caller never starts
 * consent then, see NbApp `AdsAfterAgeCheck`). Tests and screenshots use [DisabledAdsController].
 */
interface AdsController {
    /** True once consent allows requesting ads and the Mobile Ads SDK is initialised. */
    val canRequestAds: StateFlow<Boolean>

    /** Settings shows "Privacy options" while this is true (UMP privacy options requirement). */
    val privacyOptionsRequired: StateFlow<Boolean>

    val bannerAdUnitId: String?
    val interstitials: InterstitialController

    /**
     * Runs the UMP consent flow (form only where required) and starts the SDK when allowed. Call from the activity, and
     * only after the age screen: [treatment] must be known before any ad request (policy review R20).
     */
    fun gatherConsent(activity: Activity, treatment: AdAgeTreatment)

    /** Play Age Signals can tighten the treatment later; applies to the following ad requests. */
    fun updateAgeTreatment(treatment: AdAgeTreatment)

    fun showPrivacyOptions(activity: Activity)
}

object DisabledAdsController : AdsController {
    override val canRequestAds: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
    override val privacyOptionsRequired: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
    override val bannerAdUnitId: String? = null
    override val interstitials: InterstitialController = NoInterstitials

    override fun gatherConsent(activity: Activity, treatment: AdAgeTreatment) = Unit
    override fun updateAgeTreatment(treatment: AdAgeTreatment) = Unit
    override fun showPrivacyOptions(activity: Activity) = Unit
}
