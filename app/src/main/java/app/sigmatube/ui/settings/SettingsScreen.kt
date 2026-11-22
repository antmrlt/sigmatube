package app.sigmatube.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.NetworkCell
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sigmatube.data.AppSettings
import app.sigmatube.data.BackgroundMode
import androidx.compose.material.icons.outlined.Headphones
import app.sigmatube.data.HiddenChannels
import app.sigmatube.data.LocalHistory
import app.sigmatube.data.QUALITY_CHOICES
import app.sigmatube.data.ThemeMode
import app.sigmatube.data.auth.AccountManager
import app.sigmatube.data.qualityLabel

private class Choice<T>(val title: String, val options: List<T>, val selected: T, val label: (T) -> String, val onPick: (T) -> Unit)

private val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
private fun speedLabel(s: Float) = if (s == 1f) "Normale" else "${s.toString().removeSuffix(".0").replace('.', ',')}x"
private val SEEKS = listOf(5, 10, 15, 20, 30, 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    history: LocalHistory,
    hiddenChannels: HiddenChannels,
    account: AccountManager,
    appVersion: String,
    onBack: () -> Unit,
) {
    val s by settings.state.collectAsStateWithLifecycle()
    val cookies by account.cookies.collectAsStateWithLifecycle()
    val hidden by hiddenChannels.ids.collectAsStateWithLifecycle()
    val hiddenVideos by hiddenChannels.videos.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var choice by remember { mutableStateOf<Choice<*>?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paramètres", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item { Section("Général") }
            item {
                Entry(Icons.Outlined.DarkMode, "Apparence", s.theme.label) {
                    choice = Choice("Apparence", ThemeMode.entries, s.theme, { it.label }) { t -> settings.update { it.copy(theme = t) } }
                }
            }

            item { Section("Préférences de qualité vidéo") }
            item {
                Entry(Icons.Outlined.Wifi, "Sur Wi-Fi", qualityLabel(s.wifiQuality)) {
                    choice = Choice("Qualité sur Wi-Fi", QUALITY_CHOICES, s.wifiQuality, ::qualityLabel) { q -> settings.update { it.copy(wifiQuality = q) } }
                }
            }
            item {
                Entry(Icons.Outlined.NetworkCell, "Sur réseau mobile", qualityLabel(s.mobileQuality)) {
                    choice = Choice("Qualité sur réseau mobile", QUALITY_CHOICES, s.mobileQuality, ::qualityLabel) { q ->
                        settings.update { it.copy(mobileQuality = q) }
                    }
                }
            }

            item { Section("Lecture") }
            item {
                Entry(Icons.Outlined.SlowMotionVideo, "Vitesse de lecture par défaut", speedLabel(s.defaultSpeed)) {
                    choice = Choice("Vitesse par défaut", SPEEDS, s.defaultSpeed, ::speedLabel) { v -> settings.update { it.copy(defaultSpeed = v) } }
                }
            }
            item {
                Entry(Icons.Outlined.Forward10, "Avancer/reculer avec le double appui", "${s.seekSeconds} secondes") {
                    choice = Choice("Double appui", SEEKS, s.seekSeconds, { "$it secondes" }) { v -> settings.update { it.copy(seekSeconds = v) } }
                }
            }
            item {
                Entry(Icons.Outlined.Headphones, "Lecture en arrière-plan", s.background.label) {
                    choice = Choice("Continuer la lecture app fermée ou écran éteint", BackgroundMode.entries, s.background, { it.label }) { m ->
                        settings.update { it.copy(background = m) }
                    }
                }
            }
            item {
                Toggle(Icons.Outlined.LiveTv, "Masquer les lives dans l'accueil", null, s.hideLives) { v -> settings.update { it.copy(hideLives = v) } }
            }

            item { Section("Historique et confidentialité") }
            item {
                Toggle(Icons.Outlined.PauseCircle, "Suspendre l'historique des vidéos", "Les vidéos regardées ne seront plus enregistrées", s.pauseWatchHistory) { v ->
                    settings.update { it.copy(pauseWatchHistory = v) }
                }
            }
            item {
                Entry(Icons.Outlined.History, "Effacer l'historique des vidéos", null) {
                    confirm = "Effacer tout l'historique des vidéos regardées ?" to history::clearWatched
                }
            }
            item {
                Toggle(Icons.Outlined.ManageSearch, "Suspendre l'historique des recherches", null, s.pauseSearchHistory) { v ->
                    settings.update { it.copy(pauseSearchHistory = v) }
                }
            }
            item {
                Entry(Icons.Outlined.DeleteSweep, "Effacer l'historique des recherches", null) {
                    confirm = "Effacer tout l'historique des recherches ?" to history::clearSearches
                }
            }
            item {
                Entry(Icons.Outlined.Block, "Chaînes masquées", if (hidden.isEmpty()) "Aucune" else "${hidden.size} chaîne(s)") {
                    if (hidden.isNotEmpty()) {
                        confirm = "Ne plus masquer les ${hidden.size} chaîne(s) masquée(s) ?" to { hidden.forEach { id -> hiddenChannels.setHidden(id, false) } }
                    }
                }
            }
            item {
                Entry(Icons.Outlined.Block, "Vidéos « Pas intéressé »", if (hiddenVideos.isEmpty()) "Aucune" else "${hiddenVideos.size} vidéo(s)") {
                    if (hiddenVideos.isNotEmpty()) {
                        confirm = "Afficher à nouveau les ${hiddenVideos.size} vidéo(s) masquée(s) ?" to hiddenChannels::clearVideos
                    }
                }
            }

            if (cookies != null) {
                item { Section("Compte") }
                item {
                    Entry(Icons.AutoMirrored.Outlined.Logout, "Se déconnecter", null) {
                        confirm = "Se déconnecter de ton compte YouTube ?" to account::logout
                    }
                }
            }

            item { Section("À propos") }
            item { Entry(Icons.Outlined.Info, "Version", appVersion, chevron = false) {} }
            item {
                Entry(Icons.Outlined.Code, "Code source", "github.com/antmrlt/sigmatube") {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/antmrlt/sigmatube")))
                }
            }
            item { Spacer(Modifier.padding(16.dp)) }
        }
    }

    choice?.let { c -> ChoiceDialog(c) { choice = null } }
    confirm?.let { (text, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { action(); confirm = null }) { Text("Confirmer") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun <T> ChoiceDialog(c: Choice<T>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(c.title) },
        text = {
            LazyColumn {
                items(c.options) { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = option == c.selected, role = Role.RadioButton) { c.onPick(option); onDismiss() }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == c.selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(c.label(option), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

@Composable
private fun Section(title: String) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(top = 8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun Entry(icon: ImageVector, title: String, value: String?, chevron: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(24.dp))
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (value != null) {
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (chevron) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Toggle(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(24.dp))
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}
