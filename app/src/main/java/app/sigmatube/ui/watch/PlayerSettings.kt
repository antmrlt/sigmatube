package app.sigmatube.ui.watch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.sigmatube.data.StreamOptions
import app.sigmatube.data.VideoQuality

private enum class SettingsPage { Main, Quality, Speed }

private val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

private fun speedLabel(speed: Float) =
    if (speed == 1f) "Normale" else "${speed.toString().removeSuffix(".0").replace('.', ',')}x"

/** Floating, rounded settings sheet (main menu → quality / speed sub-pages). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsSheet(
    qualities: List<VideoQuality>,
    preferredHeight: Int?,
    currentQualityLabel: String?,
    speed: Float,
    loop: Boolean,
    onQuality: (Int?) -> Unit,
    onSpeed: (Float) -> Unit,
    onLoop: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var page by remember { mutableStateOf(SettingsPage.Main) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.background,
        sheetMaxWidth = 520.dp,
        modifier = Modifier.padding(horizontal = 6.dp).navigationBarsPadding().padding(bottom = 6.dp),
    ) {
        Column(Modifier.padding(bottom = 8.dp).verticalScroll(rememberScrollState())) {
            when (page) {
                SettingsPage.Main -> {
                    val autoLabel = currentQualityLabel?.let { "Auto ($it)" } ?: "Auto"
                    SettingRow(
                        Icons.Outlined.Tune, "Qualité",
                        value = if (preferredHeight == null) autoLabel else currentQualityLabel,
                        chevron = true,
                        enabled = qualities.isNotEmpty(),
                    ) { page = SettingsPage.Quality }
                    SettingRow(Icons.Outlined.SlowMotionVideo, "Vitesse de lecture", value = speedLabel(speed), chevron = true) {
                        page = SettingsPage.Speed
                    }
                    SettingRow(
                        Icons.Outlined.Repeat, "Lecture en boucle",
                        trailing = {
                            Switch(
                                checked = loop,
                                onCheckedChange = onLoop,
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        },
                    ) { onLoop(!loop) }
                }
                SettingsPage.Quality -> {
                    Header("Qualité pour la vidéo en cours", currentQualityLabel)
                    ChoiceRow("Auto (recommandé)", "Choisit automatiquement, jusqu'en ${StreamOptions.AUTO_MAX_HEIGHT}p", preferredHeight == null) {
                        onQuality(null); onDismiss()
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    qualities.distinctBy { it.height }.forEach { q ->
                        ChoiceRow(q.label, null, preferredHeight == q.height) { onQuality(q.height); onDismiss() }
                    }
                }
                SettingsPage.Speed -> {
                    Header("Vitesse de lecture", speedLabel(speed))
                    SPEEDS.forEach { s ->
                        ChoiceRow(speedLabel(s), null, s == speed) { onSpeed(s); onDismiss() }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(title: String, value: String?) {
    Row(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (value != null) {
            Text("  •  $value", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    label: String,
    value: String? = null,
    chevron: Boolean = false,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(24.dp))
        Spacer(Modifier.width(24.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (chevron) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        trailing?.invoke()
    }
}

@Composable
private fun ChoiceRow(label: String, description: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) Icon(Icons.Rounded.Check, "Sélectionné", Modifier.size(24.dp)) else Spacer(Modifier.size(24.dp))
        Spacer(Modifier.width(24.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
