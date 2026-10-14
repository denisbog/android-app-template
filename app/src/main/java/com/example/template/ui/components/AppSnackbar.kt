package com.example.template.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.example.template.ui.theme.Palette

/**
 * The app's own snackbar, dropped into a `Scaffold` in one call:
 *
 * ```kotlin
 * snackbarHost = { AppSnackbarHost(snackbarHostState) }
 * ```
 *
 * Material's default bar is an `inverseSurface` slab — a light grey, square-cornered
 * rectangle in this dark theme, unlike anything else on screen. This one is built from the
 * same parts as the rest of the app: the 10 dp card radius and 1 dp hairline, the
 * `surfaceContainer` fill and a brand dot, inset 16 dp so it lines up with the content cards.
 */
@Composable
fun AppSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState, modifier) { data -> AppSnackbar(data) }
}

/** One bar. Use [AppSnackbarHost]; reach for this only when you need your own host. */
@Composable
fun AppSnackbar(data: SnackbarData) {
    val accent = Palette.BrandBlue
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            // 16 dp matches the content list's horizontal padding, so the bar lines up with the cards
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
            .shadow(10.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, accent.copy(alpha = 0.45f), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(accent),
        )
        Text(
            text = data.visuals.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}
