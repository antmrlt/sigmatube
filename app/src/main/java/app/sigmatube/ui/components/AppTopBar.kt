package app.sigmatube.ui.components

import app.sigmatube.ui.theme.AppIcons
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sigmatube.R

/** "Σ SigmaTube" + search, shared by every top-level page. */
@Composable
fun AppTopBar(
    onSearch: () -> Unit,
    onSettings: (() -> Unit)?,
    modifier: Modifier = Modifier,
    withStatusBarPadding: Boolean = true,
) {
    Row(
        modifier
            .then(if (withStatusBarPadding) Modifier.statusBarsPadding() else Modifier)
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(width = 30.dp, height = 21.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            "SigmaTube",
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp, letterSpacing = (-0.6).sp),
            fontWeight = FontWeight.Black,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onSearch) { Icon(AppIcons.Search, "Rechercher", Modifier.size(26.dp)) }
        if (onSettings != null) {
            IconButton(onClick = onSettings) { Icon(AppIcons.Settings, "Paramètres", Modifier.size(24.dp)) }
        }
    }
}
