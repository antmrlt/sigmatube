package app.sigmatube.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A music style: shown as a coloured tile, searched with [query]. */
data class MusicGenre(val id: String, val label: String, val query: String, val color: Color)

/** A mood for the "Humeur" picker of the Musique tab. */
data class MusicMood(val id: String, val emoji: String, val label: String, val query: String)

val MUSIC_GENRES = listOf(
    MusicGenre("pop", "Pop", "pop hits clip officiel", Color(0xFFE91E63)),
    MusicGenre("rapfr", "Rap français", "rap français clip officiel", Color(0xFF3F51B5)),
    MusicGenre("hiphop", "Hip-hop US", "hip hop official music video", Color(0xFF795548)),
    MusicGenre("rnb", "R&B", "r&b official music video", Color(0xFF9C27B0)),
    MusicGenre("electro", "Électro", "electronic dance music official video", Color(0xFF00BCD4)),
    MusicGenre("house", "House", "house music official video", Color(0xFF009688)),
    MusicGenre("rock", "Rock", "rock official music video", Color(0xFFF44336)),
    MusicGenre("metal", "Metal", "metal official music video", Color(0xFF424242)),
    MusicGenre("latino", "Latino", "reggaeton video oficial", Color(0xFFFF9800)),
    MusicGenre("afro", "Afro", "afrobeats official video", Color(0xFF8BC34A)),
    MusicGenre("kpop", "K-pop", "kpop official mv", Color(0xFFFF4081)),
    MusicGenre("variete", "Variété française", "chanson française clip officiel", Color(0xFF2196F3)),
    MusicGenre("indie", "Indie", "indie official music video", Color(0xFF607D8B)),
    MusicGenre("jazz", "Jazz", "jazz live performance", Color(0xFF6D4C41)),
    MusicGenre("classique", "Classique", "classical music orchestra", Color(0xFF5D4037)),
    MusicGenre("soul", "Soul & Funk", "soul funk classic songs", Color(0xFFFFC107)),
    MusicGenre("reggae", "Reggae", "reggae official video", Color(0xFF4CAF50)),
    MusicGenre("country", "Country", "country official music video", Color(0xFFA1887F)),
    MusicGenre("lofi", "Lo-fi", "lofi hip hop beats", Color(0xFF7E57C2)),
)

val MUSIC_MOODS = listOf(
    MusicMood("chill", "😌", "Détente", "chill songs official video"),
    MusicMood("energy", "⚡", "Énergie", "upbeat songs official music video"),
    MusicMood("party", "🎉", "Fête", "party hits official music video"),
    MusicMood("workout", "💪", "Sport", "workout songs official music video"),
    MusicMood("focus", "🎧", "Concentration", "focus music instrumental"),
    MusicMood("love", "❤️", "Romance", "love songs official video"),
    MusicMood("sad", "🌧️", "Mélancolie", "sad songs official video"),
    MusicMood("sleep", "🌙", "Sommeil", "calm sleep music"),
)

/** Music styles the user picked at first use of the Musique tab. */
class MusicPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("music", Context.MODE_PRIVATE)

    private val _genres = MutableStateFlow(prefs.getStringSet(KEY_GENRES, emptySet()).orEmpty().toSet())
    val genres: StateFlow<Set<String>> = _genres

    /** False until the user went through (or skipped) the styles question. */
    private val _onboarded = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDED, false))
    val onboarded: StateFlow<Boolean> = _onboarded

    fun setGenres(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_GENRES, ids).putBoolean(KEY_ONBOARDED, true).apply()
        _genres.value = ids
        _onboarded.value = true
    }

    /** Shows the styles question again (from "Modifier mes styles"). */
    fun reopenOnboarding() {
        _onboarded.value = false
    }

    private companion object {
        const val KEY_GENRES = "genres"
        const val KEY_ONBOARDED = "onboarded"
    }
}

private val MUSIC_TITLE = Regex(
    "official (music )?video|official audio|official mv|clip officiel|audio officiel|vid[eé]o officielle|video oficial|" +
        """lyric|paroles|visuali[sz]er|music video|(^|\s)(ft|feat)\.? |remix|[(\[]audio[)\]]| - """,
    RegexOption.IGNORE_CASE,
)

/**
 * Best guess, from list data only, that a video is a song: an artist channel ("- Topic", VEVO)
 * or a music-video style title, and a song-like length. Used so the music player only moves on
 * to music.
 */
fun VideoItem.looksLikeMusic(): Boolean {
    if (isLive) return false
    if (durationSeconds >= 0 && durationSeconds !in 60..900) return false
    val channel = channelName.orEmpty()
    return channel.endsWith(" - Topic") || channel.contains("VEVO") || MUSIC_TITLE.containsMatchIn(title)
}
