package app.numbaseball

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import app.numbaseball.ads.LocalAdsController
import app.numbaseball.audio.LocalGameAudio
import app.numbaseball.audio.LocalGameHaptics
import app.numbaseball.audio.ViewGameHaptics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import app.numbaseball.ui.navigation.NbApp
import app.numbaseball.ui.theme.NumBaseballTheme

/** Single activity. AppCompat is needed for the per-app language API on Android 12 and lower. */
class MainActivity : AppCompatActivity() {
    @Volatile private var hapticsEnabled = true

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as NumBaseballApp).container
        // Ad consent and SDK start wait for the age screen (NbApp), so nothing ad-related happens before it.
        val haptics = ViewGameHaptics(window.decorView) { hapticsEnabled }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.settingsRepository.settings.collect { hapticsEnabled = it.hapticsEnabled }
            }
        }
        setContent {
            NumBaseballTheme {
                CompositionLocalProvider(
                    LocalAppContainer provides container,
                    LocalAdsController provides container.ads,
                    LocalGameAudio provides container.audio,
                    LocalGameHaptics provides haptics,
                ) {
                    NbApp(container.ageRepository, container.accountRepository)
                }
            }
        }
    }
}
