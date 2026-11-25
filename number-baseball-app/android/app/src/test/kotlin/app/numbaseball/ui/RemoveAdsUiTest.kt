package app.numbaseball.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.numbaseball.ads.AD_BANNER_TAG
import app.numbaseball.ads.AdBanner
import app.numbaseball.ads.LocalAdPreview
import app.numbaseball.billing.AdEntitlement
import app.numbaseball.billing.LocalBilling
import app.numbaseball.billing.PurchaseUiState
import app.numbaseball.billing.RemoveAdsState
import app.numbaseball.core.protocol.EndReason
import app.numbaseball.testing.FakeBillingRepository
import app.numbaseball.ui.match.MatchMode
import app.numbaseball.ui.match.MatchSummary
import app.numbaseball.ui.match.Outcome
import app.numbaseball.ui.results.REMOVE_ADS_LINK_TAG
import app.numbaseball.ui.results.ResultsScreen
import app.numbaseball.ui.settings.SettingsActions
import app.numbaseball.ui.settings.SettingsScreen
import app.numbaseball.ui.settings.SettingsUiState
import app.numbaseball.ui.theme.NumBaseballTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Remove ads entry points (11 §1.1, 06 §1) and the banner gate (DESIGN_SYSTEM 13). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h800dp")
class RemoveAdsUiTest {
    @get:Rule val compose = createComposeRule()

    private val summary = MatchSummary(
        MatchMode.QUICK, Outcome.WIN, EndReason.SOLVED, rounds = 6, opponentSecret = "3916", digits = 4, mySecret = "0472",
        opponentName = "Kestrel", myStrikes = listOf(0, 4), opponentStrikes = listOf(1, 3),
    )

    @Test fun resultLinkStartsThePurchaseAndIsHiddenWhenNotOffered() {
        var taps = 0
        var offered by mutableStateOf(true)
        compose.setContent {
            NumBaseballTheme {
                ResultsScreen(summary, showBanner = false, onRemoveAds = if (offered) ({ taps++ }) else null, onRematch = {}, onHome = {}, onBackToRoom = null, onReport = {})
            }
        }
        compose.onNodeWithTag(REMOVE_ADS_LINK_TAG).performClick()
        assertEquals(1, taps)
        offered = false
        compose.onNodeWithTag(REMOVE_ADS_LINK_TAG).assertDoesNotExist()
    }

    @Test fun noBannerOnceRemoveAdsIsOwned() {
        val billing = FakeBillingRepository()
        compose.setContent {
            NumBaseballTheme {
                CompositionLocalProvider(LocalBilling provides billing, LocalAdPreview provides { Box(Modifier.size(320.dp, 50.dp)) }) {
                    AdBanner()
                }
            }
        }
        compose.onNodeWithTag(AD_BANNER_TAG).assertExists()
        billing.entitlement.value = AdEntitlement.ADS_REMOVED
        compose.waitForIdle()
        compose.onNodeWithTag(AD_BANNER_TAG).assertDoesNotExist()
        // Not known yet (start-up): no banner either.
        billing.entitlement.value = AdEntitlement.UNKNOWN
        compose.waitForIdle()
        compose.onNodeWithTag(AD_BANNER_TAG).assertDoesNotExist()
    }

    private fun settings(purchase: PurchaseUiState, onBuy: () -> Unit = {}, onRestore: () -> Unit = {}) {
        compose.setContent {
            NumBaseballTheme {
                SettingsScreen(
                    SettingsUiState(registered = true, nickname = "Mina", tag = "0420"),
                    showPrivacyOptions = false,
                    versionName = "1.0.0 (1)",
                    contactEmail = "support@example.com",
                    languageTags = listOf("en", "ko"),
                    actions = SettingsActions({}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, onRemoveAds = onBuy, onRestorePurchase = onRestore),
                    purchase = purchase,
                )
            }
        }
    }

    @Test fun settingsShowsPlaysPriceAndStartsThePurchase() {
        var buys = 0
        var restores = 0
        settings(PurchaseUiState(RemoveAdsState.Available("$4.99")), onBuy = { buys++ }, onRestore = { restores++ })
        compose.onNodeWithTag("removeAdsRow").performScrollTo().performClick()
        compose.onNodeWithTag("restorePurchase").performScrollTo().performClick()
        assertEquals(1, buys)
        assertEquals(1, restores)
    }

    @Test fun purchasedHidesRestoreAndIsNotTappable() {
        settings(PurchaseUiState(RemoveAdsState.Owned))
        compose.onNodeWithText("Purchased · no ads").performScrollTo()
        compose.onNodeWithTag("restorePurchase").assertDoesNotExist()
        compose.onNodeWithTag("removeAdsRow").assertDoesNotExist()
    }

    @Test fun pendingKeepsRestoreAndShowsTheNote() {
        settings(PurchaseUiState(RemoveAdsState.Pending))
        compose.onNodeWithText("Payment pending · ads go away once it clears").performScrollTo()
        compose.onNodeWithTag("restorePurchase").assertExists()
    }
}
