package app.sigmatube

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sigmatube.ui.navigation.SigmaNavHost
import app.sigmatube.ui.theme.SigmaTubeTheme
import app.sigmatube.data.ThemeMode
import androidx.compose.foundation.isSystemInDarkTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.schabi.newpipe.extractor.ServiceList

class MainActivity : ComponentActivity() {

    /** A YouTube video URL received via the share sheet, waiting to be opened. */
    private val pendingVideoUrl = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        // Let the logo animation play out before revealing the app.
        val start = SystemClock.uptimeMillis()
        splash.setKeepOnScreenCondition { SystemClock.uptimeMillis() - start < SPLASH_MIN_MS }
        splash.setOnExitAnimationListener { provider ->
            provider.iconView.animate().scaleX(1.5f).scaleY(1.5f).alpha(0f).setDuration(260).start()
            provider.view.animate().alpha(0f).setDuration(300).withEndAction { provider.remove() }.start()
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SigmaTubeApp).container
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val settings by container.settings.state.collectAsStateWithLifecycle()
            val dark = when (settings.theme) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            SigmaTubeTheme(darkTheme = dark) {
                val pending by pendingVideoUrl.collectAsStateWithLifecycle()
                // Surface (not a plain Box) so every Text/Icon inherits the theme's content colour,
                // including the video overlay which isn't inside a Scaffold.
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SigmaNavHost(
                        container = container,
                        pendingVideoUrl = pending,
                        onPendingVideoConsumed = { pendingVideoUrl.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val url = URL_REGEX.find(text)?.value ?: return
        val accepted = runCatching { ServiceList.YouTube.streamLHFactory.acceptUrl(url) }.getOrDefault(false)
        if (accepted) pendingVideoUrl.value = url
    }

    private companion object {
        val URL_REGEX = Regex("""https?://\S+""")
        const val SPLASH_MIN_MS = 600L
    }
}
