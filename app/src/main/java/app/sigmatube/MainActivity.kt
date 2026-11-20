package app.sigmatube

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sigmatube.ui.navigation.SigmaNavHost
import app.sigmatube.ui.theme.SigmaTubeTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.schabi.newpipe.extractor.ServiceList

class MainActivity : ComponentActivity() {

    /** A YouTube video URL received via the share sheet, waiting to be opened. */
    private val pendingVideoUrl = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SigmaTubeApp).container
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            SigmaTubeTheme {
                val pending by pendingVideoUrl.collectAsStateWithLifecycle()
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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
    }
}
