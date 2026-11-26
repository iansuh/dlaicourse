package app.numbaseball

import android.os.Bundle
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.numbaseball.ads.LocalAdsController
import app.numbaseball.audio.LocalGameAudio
import app.numbaseball.billing.LocalBilling
import app.numbaseball.audio.LocalGameHaptics
import app.numbaseball.audio.ViewGameHaptics
import app.numbaseball.ui.design.LocalReduceMotion
import app.numbaseball.ui.navigation.NbApp
import app.numbaseball.ui.theme.NumBaseballTheme
import kotlinx.coroutines.launch

/** Single activity. AppCompat is needed for the per-app language API on Android 12 and lower. */
class MainActivity : AppCompatActivity() {
    @Volatile private var hapticsEnabled = true

    override fun onCreate(savedInstanceState: Bundle?) {
        // DESIGN_SYSTEM 1.1: light window, transparent bars with dark icons whatever the system dark mode says.
        val bars = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        val container = (application as NumBaseballApp).container
        // Ad consent and SDK start wait for the age screen (NbApp), so nothing ad-related happens before it.
        // Remove ads: entitlement from Play at start and on every resume (refunds, pending payments, other devices).
        container.billing.start()
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) = container.billing.refresh()
            },
        )
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        val haptics = ViewGameHaptics(window.decorView, vibrator) { hapticsEnabled }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.settingsRepository.settings.collect { hapticsEnabled = it.hapticsEnabled }
            }
        }
        // DESIGN_SYSTEM 8.4: "Remove animations" (animator duration scale 0) switches to the reduced-motion variants.
        val reduceMotion = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        setContent {
            NumBaseballTheme {
                CompositionLocalProvider(
                    LocalAppContainer provides container,
                    LocalAdsController provides container.ads,
                    LocalBilling provides container.billing,
                    LocalGameAudio provides container.audio,
                    LocalGameHaptics provides haptics,
                    LocalReduceMotion provides reduceMotion,
                ) {
                    NbApp(container.ageRepository, container.accountRepository)
                }
            }
        }
    }
}
