package app.sigmatube.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Springy defaults shared by the app's micro-interactions. */
object Motion {
    val pressSpring = spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)
    const val FADE_MS = 220
}

/**
 * Clickable that also sinks slightly under the finger and springs back, for cards and pills.
 * Keeps the theme's ripple.
 */
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    this
        .pressScale(source, pressedScale)
        .clickable(interactionSource = source, indication = LocalIndication.current, enabled = enabled, onClick = onClick)
}

/** Just the press-sink effect, for when the clickable must sit after a clip (rounded ripple). */
fun Modifier.pressScale(source: MutableInteractionSource, pressedScale: Float = 0.97f): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, Motion.pressSpring, label = "press")
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Scale that "pops" (1 → 1.35 → 1) each time [key] changes, e.g. when an icon gets toggled on. */
@Composable
fun rememberPopScale(key: Any?, enabled: Boolean = true): Float {
    val scale = remember { Animatable(1f) }
    var first by remember { androidx.compose.runtime.mutableStateOf(true) }
    LaunchedEffect(key) {
        if (first) { first = false; return@LaunchedEffect }
        if (!enabled) return@LaunchedEffect
        scale.animateTo(1f, keyframes {
            durationMillis = 360
            0.8f at 70
            1.3f at 180
            1f at 360
        })
    }
    return scale.value
}

/** Animated light sweep used by the loading skeletons. */
fun Modifier.shimmer(): Modifier = composed {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -600f,
        targetValue = 1600f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX",
    )
    background(
        Brush.linearGradient(
            colors = listOf(base, highlight, base),
            start = Offset(x, 0f),
            end = Offset(x + 500f, 250f),
        ),
    )
}

/** Placeholder shaped like a feed card (thumbnail, avatar, two text lines). */
@Composable
fun VideoCardSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 24.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).shimmer())
        Row(Modifier.padding(top = 12.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).shimmer())
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth(0.9f).height(14.dp).clip(RoundedCornerShape(7.dp)).shimmer())
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(0.55f).height(12.dp).clip(RoundedCornerShape(6.dp)).shimmer())
            }
        }
    }
}

/** Placeholder shaped like a compact row (small thumbnail + text). */
@Composable
fun VideoRowSkeleton(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Box(Modifier.width(160.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).shimmer())
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(top = 4.dp)) {
            Box(Modifier.fillMaxWidth(0.95f).height(13.dp).clip(RoundedCornerShape(7.dp)).shimmer())
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth(0.7f).height(13.dp).clip(RoundedCornerShape(7.dp)).shimmer())
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(0.45f).height(11.dp).clip(RoundedCornerShape(6.dp)).shimmer())
        }
    }
}

/** A few card skeletons as lazy-list items, while a feed loads. */
fun LazyListScope.feedSkeleton(count: Int = 3, rows: Boolean = false) {
    items(count, key = { "skeleton-$it" }, contentType = { "skeleton" }) {
        if (rows) VideoRowSkeleton() else VideoCardSkeleton()
    }
}

/** Full-screen loading placeholder for feeds: a few skeleton cards (or rows) with a shimmer. */
@Composable
fun SkeletonFeed(modifier: Modifier = Modifier, rows: Boolean = false, count: Int = if (rows) 7 else 3) {
    Column(modifier.fillMaxWidth().clipToBounds().padding(top = 8.dp)) {
        repeat(count) { if (rows) VideoRowSkeleton() else VideoCardSkeleton() }
    }
}
