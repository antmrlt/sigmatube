package app.sigmatube

import android.app.Application
import app.sigmatube.data.AppSettings
import app.sigmatube.data.HiddenChannels
import app.sigmatube.data.LocalHistory
import app.sigmatube.data.WatchProgress
import app.sigmatube.data.YouTubeRepository
import app.sigmatube.data.auth.AccountManager
import app.sigmatube.data.auth.InnerTubeClient
import app.sigmatube.data.extractor.OkHttpDownloader
import app.sigmatube.player.PlayerManager
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale
import java.util.concurrent.TimeUnit

class SigmaTubeApp : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.httpClient })) }
            .crossfade(true)
            .build()
}

/** Manual dependency container. Small enough that a DI framework isn't worth it yet. */
class AppContainer(app: Application) {

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    init {
        val locale = Locale.getDefault()
        NewPipe.init(
            OkHttpDownloader(httpClient),
            Localization.fromLocale(locale),
            ContentCountry(locale.country.ifBlank { "US" }),
        )
    }

    val repository = YouTubeRepository()
    val playerManager = PlayerManager(app)
    val account = AccountManager(app)
    val innerTube = InnerTubeClient(httpClient, account)
    val hiddenChannels = HiddenChannels(app)
    val settings = AppSettings(app)
    val history = LocalHistory(app, settings)
    val watchProgress = WatchProgress(app)
}
