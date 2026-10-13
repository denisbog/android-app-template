package com.example.template.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Moving highlight used by the sidebar skeleton while a load runs, and by the "Loading…"
 * status line.
 */
@Composable
fun rememberShimmerBrush(
    baseColor: Color,
    highlightColor: Color = baseColor.copy(alpha = 1f),
    durationMillis: Int = 1500,
    sweepWidth: Float = 320f,
): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )
    return remember(progress, baseColor, highlightColor, sweepWidth) {
        val start = -sweepWidth + progress * sweepWidth * 2.4f
        Brush.linearGradient(
            colors = listOf(baseColor, highlightColor, baseColor),
            start = Offset(start, 0f),
            end = Offset(start + sweepWidth, 0f),
        )
    }
}

/** Fade + slide-in for a newly drawn card, matching the reference app's row animation. */
@Composable
fun AppearIn(
    modifier: Modifier = Modifier,
    durationMillis: Int = 260,
    index: Int = 0,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = durationMillis, delayMillis = index * 40),
        )
    }
    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 24f
        },
    ) {
        content()
    }
}

/** Pulsing dot used next to the "Loading…" status while a request is in flight. */
@Composable
fun PulseDot(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )
    Canvas(modifier) {
        drawCircle(color = color, radius = size.minDimension / 2f, alpha = alpha)
    }
}
