package app.numbaseball.ads

import android.app.Activity
import app.numbaseball.data.age.AdAgeTreatment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Told when a vs-computer match ends (never for online matches); feeds the interstitial frequency cap. */
fun interface ComputerMatchFinishedListener {
    fun onComputerMatchFinished()
}

interface InterstitialController : ComputerMatchFinishedListener {
    /**
     * Call only when the player leaves a vs-computer result screen. Shows an interstitial if one is loaded and the cap
     * allows it, then calls [onContinue] (immediately if not). [resultVisibleMillis]: how long the result was on screen.
     */
    fun showIfDue(activity: Activity, resultVisibleMillis: Long, onContinue: () -> Unit)
}

/**
 * Ads per README "Monetization": banners only on calm screens, interstitials only after vs-computer matches, muted,
 * consent through UMP. Tests and screenshots use [DisabledAdsController].
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
    override val interstitials: InterstitialController = object : InterstitialController {
        override fun onComputerMatchFinished() = Unit
        override fun showIfDue(activity: Activity, resultVisibleMillis: Long, onContinue: () -> Unit) = onContinue()
    }

    override fun gatherConsent(activity: Activity, treatment: AdAgeTreatment) = Unit
    override fun updateAgeTreatment(treatment: AdAgeTreatment) = Unit
    override fun showPrivacyOptions(activity: Activity) = Unit
}
