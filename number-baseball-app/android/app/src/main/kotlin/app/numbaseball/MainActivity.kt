package app.numbaseball

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import app.numbaseball.ads.LocalAdsController
import app.numbaseball.audio.LocalGameAudio
import app.numbaseball.ui.navigation.NbApp
import app.numbaseball.ui.theme.NumBaseballTheme

/** Single activity. AppCompat is needed for the per-app language API on Android 12 and lower. */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as NumBaseballApp).container
        // Ad consent and SDK start wait for the age screen (NbApp), so nothing ad-related happens before it.
        setContent {
            NumBaseballTheme {
                CompositionLocalProvider(
                    LocalAppContainer provides container,
                    LocalAdsController provides container.ads,
                    LocalGameAudio provides container.audio,
                ) {
                    NbApp(container.ageRepository, container.accountRepository)
                }
            }
        }
    }
}
