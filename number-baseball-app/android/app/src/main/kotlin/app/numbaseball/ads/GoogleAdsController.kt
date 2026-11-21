package app.numbaseball.ads

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import app.numbaseball.data.age.AdAgeTreatment
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AgeRestrictedTreatment
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * AdMob + UMP. Order follows Google's guidance: request consent info on every start, show the form only if required,
 * initialise the Mobile Ads SDK off the main thread once `canRequestAds()` is true (also true for non-personalised ads
 * after a refusal). Ads are always muted, capped at the build-config content rating and follow the age treatment from the
 * first-launch age screen (and Play Age Signals). Sensitive categories are blocked in the AdMob console (no SDK switch).
 */
class GoogleAdsController(
    private val appContext: Context,
    override val bannerAdUnitId: String,
    interstitialAdUnitId: String,
    private val scope: CoroutineScope,
    /** "G", "PG", "T" or "MA" from build config; must not exceed the app's IARC rating (policy review R20). */
    private val maxAdContentRating: String,
) : AdsController {
    private val consentInformation: ConsentInformation = UserMessagingPlatform.getConsentInformation(appContext)
    private val consentRequested = AtomicBoolean(false)
    private val sdkStarted = AtomicBoolean(false)

    private val _canRequestAds = MutableStateFlow(false)
    override val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)
    override val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    override val interstitials: InterstitialController = GoogleInterstitialController(
        appContext = appContext,
        adUnitId = interstitialAdUnitId,
        policy = InterstitialPolicy(now = SystemClock::elapsedRealtime),
        canRequestAds = { _canRequestAds.value },
    )

    override fun gatherConsent(activity: Activity, treatment: AdAgeTreatment) {
        // Once per process; activity recreation (rotation, language change) must not show the form again.
        if (!consentRequested.compareAndSet(false, true)) return
        applyRequestConfiguration(treatment)
        val params = ConsentRequestParameters.Builder()
            // Under 16: under the age of consent, so UMP does not ask and ads are not personalized.
            .setTagForUnderAgeOfConsent(treatment == AdAgeTreatment.CHILD)
            .build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ -> onConsentKnown() }
            },
            { _ -> onConsentKnown() },
        )
        // Consent from a previous session may already allow ads; don't wait for the network round trip.
        if (consentInformation.canRequestAds()) startSdk()
    }

    override fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { _ -> onConsentKnown() }
    }

    private fun onConsentKnown() {
        _privacyOptionsRequired.value =
            consentInformation.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        if (consentInformation.canRequestAds()) startSdk()
    }

    override fun updateAgeTreatment(treatment: AdAgeTreatment) = applyRequestConfiguration(treatment)

    /** Age treatment and content cap, set before the SDK starts and before every later change (R20). */
    private fun applyRequestConfiguration(treatment: AdAgeTreatment) {
        val builder = MobileAds.getRequestConfiguration().toBuilder()
            .setMaxAdContentRating(maxAdContentRating)
        when (treatment) {
            // CHILD replaces the deprecated child-directed / under-age-of-consent tags in SDK 25.x.
            AdAgeTreatment.CHILD -> builder
                .setAgeRestrictedTreatment(AgeRestrictedTreatment.CHILD)
                .setPublisherPrivacyPersonalizationState(RequestConfiguration.PublisherPrivacyPersonalizationState.DISABLED)
            AdAgeTreatment.TEEN -> builder.setAgeRestrictedTreatment(AgeRestrictedTreatment.TEEN)
            AdAgeTreatment.UNSPECIFIED -> builder.setAgeRestrictedTreatment(AgeRestrictedTreatment.UNSPECIFIED)
        }
        MobileAds.setRequestConfiguration(builder.build())
    }

    private fun startSdk() {
        if (!sdkStarted.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) {
            MobileAds.initialize(appContext) { _ ->
                // README "Sound": ads stay muted whatever the game's own sound settings are.
                MobileAds.setAppMuted(true)
                _canRequestAds.value = true
            }
        }
    }
}

/** Main-thread only. Loads one interstitial ahead and shows it when the [policy] allows. */
private class GoogleInterstitialController(
    private val appContext: Context,
    private val adUnitId: String,
    private val policy: InterstitialPolicy,
    private val canRequestAds: () -> Boolean,
) : InterstitialController {
    private var ad: InterstitialAd? = null
    private var loading = false

    override fun onComputerMatchFinished() {
        policy.onComputerMatchFinished()
        if (policy.shouldPreload()) preload()
    }

    override fun showIfDue(activity: Activity, resultVisibleMillis: Long, onContinue: () -> Unit) {
        val ready = ad
        if (ready == null || !policy.shouldShow(resultVisibleMillis)) {
            if (policy.shouldPreload()) preload()
            onContinue()
            return
        }
        ad = null
        policy.onShown()
        ready.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = onContinue()
            override fun onAdFailedToShowFullScreenContent(error: AdError) = onContinue()
        }
        ready.show(activity)
    }

    private fun preload() {
        if (ad != null || loading || !canRequestAds()) return
        loading = true
        InterstitialAd.load(
            appContext,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    loading = false
                    ad = interstitialAd
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                }
            },
        )
    }
}
