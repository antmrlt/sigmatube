package app.sigmatube.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sigmatube.data.LocalHiddenChannels
import app.sigmatube.data.auth.MusicCard
import app.sigmatube.data.auth.MusicSearchResult
import app.sigmatube.data.withoutHidden
import app.sigmatube.ui.components.MessageState
import app.sigmatube.ui.components.bouncyClickable

/** Search results of the Musique tab: best match, songs, artists, albums. */
@Composable
fun MusicSearchResults(result: MusicSearchResult, nav: MusicNavigation, modifier: Modifier = Modifier) {
    val songs = result.songs.withoutHidden(LocalHiddenChannels.current)
    var showAllSongs by remember(result) { mutableStateOf(false) }
    if (result.top == null && songs.isEmpty() && result.artists.isEmpty() && result.albums.isEmpty()) {
        MessageState("Aucun résultat", "Essaie avec un autre titre ou un autre artiste.", modifier, icon = Icons.Rounded.SearchOff)
        return
    }
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 24.dp)) {
        result.top?.let { top ->
            item(key = "top") {
                MusicSectionTitle("Meilleur résultat")
                TopResult(top) {
                    when {
                        top.artistId != null && top.albumId == null -> nav.artist(top.artistId)
                        top.albumId != null -> nav.album(top.albumId)
                        top.videoId != null -> {
                            val start = songs.indexOfFirst { it.url.endsWith(top.videoId) }
                            if (start >= 0) nav.play(songs, start, false)
                            else top.toVideoItem(null)?.let { nav.play(listOf(it) + songs, 0, false) }
                        }
                    }
                }
            }
        }
        if (songs.isNotEmpty()) {
            item(key = "songs-title") { MusicSectionTitle("Titres") }
            val shown = if (showAllSongs) songs else songs.take(6)
            itemsIndexed(shown, key = { _, v -> "s:${v.url}" }) { i, v -> SongRow(v, onClick = { nav.play(songs, i, false) }) }
            if (songs.size > 6 && !showAllSongs) {
                item(key = "more") {
                    Text(
                        "Afficher plus",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .padding(start = 16.dp, top = 4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { showAllSongs = true }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
        if (result.artists.isNotEmpty()) {
            item(key = "artists") {
                MusicSectionTitle("Artistes")
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(result.artists, key = { it.artistId ?: it.title }) { a ->
                        ArtistCircle(a.title, a.thumbnail, onClick = { a.artistId?.let(nav.artist) })
                    }
                }
            }
        }
        if (result.albums.isNotEmpty()) {
            item(key = "albums") {
                MusicSectionTitle("Albums")
                MusicCardsRow(result.albums) { card -> card.albumId?.let(nav.album) }
            }
        }
    }
}

@Composable
private fun TopResult(card: MusicCard, onClick: () -> Unit) {
    val isArtist = card.artistId != null && card.albumId == null && card.videoId == null
    Row(
        Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .bouncyClickable(pressedScale = 0.98f, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(card.thumbnail, Modifier.size(80.dp), if (isArtist) CircleShape else RoundedCornerShape(10.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(card.title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
            card.subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(
            if (card.videoId != null) Icons.Rounded.PlayArrow else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            null,
            Modifier.size(28.dp),
        )
    }
}
