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
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import app.sigmatube.R

/** Brand accent: progress bars, live badges, links. */
val Accent = Color(0xFFFF0033)

/** Figtree (OFL), bundled as one variable font: each weight is a variation of the same file. */
@OptIn(ExperimentalTextApi::class)
val Figtree = FontFamily(
    listOf(400, 500, 600, 700, 800, 900).map { w ->
        Font(R.font.figtree, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

// Flat, neutral palette: content (thumbnails) carries the color, chrome stays out of the way.
private val DarkColors = darkColorScheme(
    primary = Color(0xFFF1F1F1),
    onPrimary = Color(0xFF0F0F0F),
    secondary = Color(0xFFAAAAAA),
    tertiary = Color(0xFF5AB0FF),
    background = Color(0xFF0C0C0E),
    onBackground = Color(0xFFF1F1F1),
    surface = Color(0xFF0C0C0E),
    onSurface = Color(0xFFF1F1F1),
    surfaceVariant = Color(0xFF26262C),
    onSurfaceVariant = Color(0xFFAAAAAA),
    surfaceContainerLowest = Color(0xFF0C0C0E),
    surfaceContainerLow = Color(0xFF16161A),
    surfaceContainer = Color(0xFF1D1D22),
    surfaceContainerHigh = Color(0xFF26262C),
    surfaceContainerHighest = Color(0xFF3A3A42),
    outline = Color(0xFF3A3A42),
    outlineVariant = Color(0xFF26262C),
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

private fun TextStyle.figtree(weight: Int, size: Int, line: Int, spacing: Double = 0.0) = copy(
    fontFamily = Figtree,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = spacing.sp,
)

private val AppTypography = Typography(
    displaySmall = base.displaySmall.figtree(800, 34, 40, -0.6),
    headlineMedium = base.headlineMedium.figtree(800, 28, 34, -0.5),
    headlineSmall = base.headlineSmall.figtree(800, 24, 30, -0.4),
    titleLarge = base.titleLarge.figtree(700, 20, 26, -0.2),
    titleMedium = base.titleMedium.figtree(600, 16, 22, -0.1),
    titleSmall = base.titleSmall.figtree(600, 15, 20),
    bodyLarge = base.bodyLarge.figtree(400, 16, 23),
    bodyMedium = base.bodyMedium.figtree(400, 14, 20),
    bodySmall = base.bodySmall.figtree(500, 12, 17, 0.1),
    labelLarge = base.labelLarge.figtree(600, 14, 20),
    labelMedium = base.labelMedium.figtree(600, 12, 16, 0.2),
    labelSmall = base.labelSmall.figtree(600, 11, 14, 0.3),
)

@Composable
fun SigmaTubeTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
