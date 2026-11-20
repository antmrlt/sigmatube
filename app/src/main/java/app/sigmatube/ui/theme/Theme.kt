package app.sigmatube.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Brand accent: progress bars, live badges, links. */
val Accent = Color(0xFFFF0033)

// Flat, neutral palette: content (thumbnails) carries the color, chrome stays out of the way.
private val DarkColors = darkColorScheme(
    primary = Color(0xFFF1F1F1),
    onPrimary = Color(0xFF0F0F0F),
    secondary = Color(0xFFAAAAAA),
    tertiary = Color(0xFF3EA6FF),
    background = Color(0xFF0F0F0F),
    onBackground = Color(0xFFF1F1F1),
    surface = Color(0xFF0F0F0F),
    onSurface = Color(0xFFF1F1F1),
    surfaceVariant = Color(0xFF272727),
    onSurfaceVariant = Color(0xFFAAAAAA),
    surfaceContainerLowest = Color(0xFF0F0F0F),
    surfaceContainerLow = Color(0xFF181818),
    surfaceContainer = Color(0xFF212121),
    surfaceContainerHigh = Color(0xFF272727),
    surfaceContainerHighest = Color(0xFF3F3F3F),
    outline = Color(0xFF3F3F3F),
    outlineVariant = Color(0xFF272727),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F0F0F),
    onPrimary = Color.White,
    secondary = Color(0xFF606060),
    tertiary = Color(0xFF065FD4),
    background = Color.White,
    onBackground = Color(0xFF0F0F0F),
    surface = Color.White,
    onSurface = Color(0xFF0F0F0F),
    surfaceVariant = Color(0xFFF2F2F2),
    onSurfaceVariant = Color(0xFF606060),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9F9F9),
    surfaceContainer = Color(0xFFF2F2F2),
    surfaceContainerHigh = Color(0xFFF2F2F2),
    surfaceContainerHighest = Color(0xFFE5E5E5),
    outline = Color(0xFFD9D9D9),
    outlineVariant = Color(0xFFE5E5E5),
)

private val base = Typography()

private val AppTypography = Typography(
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = base.bodyLarge.copy(fontSize = 16.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = base.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp),
)

@Composable
fun SigmaTubeTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
