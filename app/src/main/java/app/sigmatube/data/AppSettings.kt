package app.sigmatube.data

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class BackgroundMode(val label: String) {
    Music("Musique uniquement"),
    Always("Toujours"),
    Never("Jamais"),
}

enum class ThemeMode(val label: String) { System("Thème de l'appareil"), Light("Thème clair"), Dark("Thème sombre") }

/** Quality caps offered in settings; 0 = "Auto" (720p cap). */
val QUALITY_CHOICES = listOf(0, 2160, 1440, 1080, 720, 480, 360, 240, 144)

fun qualityLabel(height: Int) = if (height == 0) "Auto (1080p max)" else "${height}p max"

data class Settings(
    val theme: ThemeMode = ThemeMode.System,
    val wifiQuality: Int = 0,
    val mobileQuality: Int = 480,
    val defaultSpeed: Float = 1f,
    val seekSeconds: Int = 10,
    val hideLives: Boolean = true,
    val pauseWatchHistory: Boolean = false,
    val pauseSearchHistory: Boolean = false,
    val autoplay: Boolean = true,
    val captions: Boolean = false,
    val background: BackgroundMode = BackgroundMode.Music,
)

/** User preferences, persisted in SharedPreferences and observable from the UI. */
class AppSettings(private val context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<Settings> = _state

    private fun read() = Settings(
        theme = runCatching { ThemeMode.valueOf(prefs.getString("theme", null) ?: "") }.getOrDefault(ThemeMode.System),
        wifiQuality = prefs.getInt("wifiQuality", 0),
        mobileQuality = prefs.getInt("mobileQuality", 480),
        defaultSpeed = prefs.getFloat("defaultSpeed", 1f),
        seekSeconds = prefs.getInt("seekSeconds", 10),
        hideLives = prefs.getBoolean("hideLives", true),
        pauseWatchHistory = prefs.getBoolean("pauseWatchHistory", false),
        pauseSearchHistory = prefs.getBoolean("pauseSearchHistory", false),
        autoplay = prefs.getBoolean("autoplay", true),
        captions = prefs.getBoolean("captions", false),
        background = runCatching { BackgroundMode.valueOf(prefs.getString("background", null) ?: "") }.getOrDefault(BackgroundMode.Music),
    )

    fun update(transform: (Settings) -> Settings) {
        val s = transform(_state.value)
        prefs.edit()
            .putString("theme", s.theme.name)
            .putInt("wifiQuality", s.wifiQuality)
            .putInt("mobileQuality", s.mobileQuality)
            .putFloat("defaultSpeed", s.defaultSpeed)
            .putInt("seekSeconds", s.seekSeconds)
            .putBoolean("hideLives", s.hideLives)
            .putBoolean("pauseWatchHistory", s.pauseWatchHistory)
            .putBoolean("pauseSearchHistory", s.pauseSearchHistory)
            .putBoolean("autoplay", s.autoplay)
            .putBoolean("captions", s.captions)
            .putString("background", s.background.name)
            .apply()
        _state.value = s
    }

    /** Max height for "Auto", depending on whether the current network is metered. */
    fun autoMaxHeight(): Int {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val metered = cm?.isActiveNetworkMetered ?: false
        val choice = if (metered) _state.value.mobileQuality else _state.value.wifiQuality
        return if (choice == 0) StreamOptions.AUTO_MAX_HEIGHT else choice
    }
}
