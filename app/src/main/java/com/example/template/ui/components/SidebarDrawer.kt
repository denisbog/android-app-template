package com.example.template.ui.components

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Width of the invisible strip along the left edge that opens the drawer. */
private val EdgeDragWidth = 44.dp


/**
 * Drag-to-open from the left edge.
 *
 * Only a touch that starts within [edgePx] of the left edge is considered, and only once it
 * has travelled past the horizontal touch slop **to the right** — so vertical scrolling and
 * leftward drags that merely start near the edge are left to the content underneath. The
 * settle thresholds in [SidebarDrawerState.settle] are deliberately forgiving: a short pull is
 * enough to open the drawer.
 *
 * Returns the release velocity so the caller — which runs outside the restricted pointer
 * scope — can settle the drawer.
 */
private suspend fun AwaitPointerEventScope.dragDrawerFromEdge(
    state: SidebarDrawerState,
    edgePx: Float,
): Float? {
    val down = awaitFirstDown(requireUnconsumed = false)
    if (state.isOpen || down.position.x > edgePx) return null  // open already, or not from the edge

    var rightward = false
    awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
        if (overSlop > 0f) {                                  // rightward: this is our gesture
            rightward = true
            change.consume()
        }
    } ?: return null                                          // released, or dragged left
    if (!rightward) return null

    var last = down.uptimeMillis
    var velocity = 0f
    drag(down.id) { change ->
        val dt = (change.uptimeMillis - last).coerceAtLeast(1L)
        velocity = velocity * 0.6f + (change.positionChange().x / dt * 1000f) * 0.4f
        last = change.uptimeMillis
        state.dragBy(change.positionChange().x)
        change.consume()
    }
    return velocity
}

/**
 * Modal sidebar drawer — panel, scrim, gestures and back handling in one place.
 *
 *  * the panel follows the finger (fraction 0..1) and snaps open/closed on distance or fling;
 *  * a **white** wash and a slight scale/rotation follow that fraction, so the screen behind
 *    fades and zooms out instead of being pushed aside;
 *  * drag-to-open from the left edge, drag-to-close from the panel or the scrim, tap the
 *    scrim to close;
 *  * system back and predictive back (Android 13+) drive the same fraction, and while a text
 *    field inside the panel has focus back only dismisses the keyboard;
 *  * on wide screens a **docked** sidebar replaces the overlay (see [SidebarDrawerHost]).
 *
 * The four non-obvious parts of this file — why the edge drag lives on the *content*
 * wrapper, why there is a separate `systemGestureExclusion()` marker, why the snap
 * thresholds are low, and why the settle call cannot live inside the pointer scope — are
 * written up in `docs/PITFALLS.md`. Read that before changing any of them.
 */
@Stable
class SidebarDrawerState(panelWidthPx: Float) {

    private var panelWidthPx: Float = panelWidthPx

    /** The drawer width can change with the window size. */
    fun updatePanelWidth(px: Float) {
        if (px > 0f) panelWidthPx = px
    }

    private val anim = Animatable(0f)

    private var currentFraction by mutableFloatStateOf(0f)

    /** 0 = closed, 1 = open. */
    val fraction: Float get() = currentFraction

    var isOpen by mutableStateOf(false)
        private set

    val scrimAlpha: Float get() = fraction * 0.16f

    val contentScale: Float get() = 1f - fraction * 0.04f

    val contentRotationY: Float get() = -fraction * 6f

    val contentOffsetPx: Float get() = if (moveContent) fraction * contentShiftPx else 0f

    val contentCornerPx: Float get() = cornerPx

    /** When false the main screen stays exactly where it is (only the corner clip applies). */
    var moveContent: Boolean = true

    private var contentShiftPx: Float = 640f
    private var cornerPx: Float = 0f

    fun updateMetrics(screenWidthPx: Float, cornerRadiusPx: Float) {
        if (screenWidthPx > 0f) contentShiftPx = screenWidthPx * 0.8f + 16f
        if (cornerRadiusPx > 0f) cornerPx = cornerRadiusPx
    }

    /** Slide distance of the screen: it moves out to the right by the drawer's width. */
    fun updateShift(shiftPx: Float) {
        if (shiftPx > 0f) contentShiftPx = shiftPx
    }

    fun useDeviceCornerRadius(radiusPx: Float, fallbackPx: Float) {
        cornerPx = if (radiusPx > 0f) radiusPx else fallbackPx
    }

    /** Used by drags and by predictive back to drive the open fraction directly. */
    fun updateFraction(value: Float) {
        currentFraction = value.coerceIn(0f, 1f)
    }

    fun dragBy(deltaPx: Float) {
        updateFraction(fraction + deltaPx / panelWidthPx)
    }

    /**
     * Which way the drag settles. The thresholds are deliberately low: opening a drawer from
     * the edge is a short gesture, so a fifth of the panel width (or a gentle fling) is enough
     * to open it, while closing needs an equally deliberate move. Material's defaults
     * (`0.5f` / `900f`) make a 120 dp pull snap back and read as "the swipe doesn't work".
     */
    suspend fun settle(velocityPxPerSecond: Float) {
        val target = when {
            velocityPxPerSecond > 450f -> 1f
            velocityPxPerSecond < -450f -> 0f
            fraction > 0.22f -> 1f
            else -> 0f
        }
        isOpen = target == 1f
        animateTo(target)
    }

    suspend fun open() {
        isOpen = true
        animateTo(1f)
    }

    suspend fun close() {
        isOpen = false
        animateTo(0f)
    }

    /** A cancelled predictive-back gesture snaps back to open. */
    suspend fun restore() {
        isOpen = true
        animateTo(1f)
    }

    private suspend fun animateTo(target: Float) {
        anim.snapTo(fraction)
        anim.animateTo(
            targetValue = target,
            animationSpec = tween(
                durationMillis = if (target > fraction) 280 else 220,
                easing = FastOutSlowInEasing,
            ),
        ) {
            currentFraction = value
        }
    }
}

/**
 * The device's own screen corner radius (`Display.getRoundedCorner`, API 31+), so the main
 * content keeps the phone's roundness while it slides out. Falls back to [fallback] when the
 * display does not report one (emulators usually report 0).
 */
@Composable
fun rememberDeviceCornerRadius(fallback: Dp = 28.dp): Dp {
    val context = LocalContext.current
    val density = LocalDensity.current
    return remember(fallback) {
        val px = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val display = context.display
                listOf(
                    android.view.RoundedCorner.POSITION_TOP_LEFT,
                    android.view.RoundedCorner.POSITION_TOP_RIGHT,
                ).maxOfOrNull { display?.getRoundedCorner(it)?.radius ?: 0 } ?: 0
            } else {
                0
            }
        }.getOrDefault(0)
        val radiusPx = if (px > 0) px.toFloat() else with(density) { fallback.toPx() }
        with(density) { radiusPx.toDp() }
    }
}

@Composable
fun rememberSidebarDrawerState(panelWidth: Dp = 320.dp): SidebarDrawerState {
    val density = LocalDensity.current
    val panelWidthPx = with(density) { panelWidth.toPx() }
    return remember(panelWidthPx) { SidebarDrawerState(panelWidthPx) }
}

/**
 * Modal sidebar: the panel slides in from the left over `content`, following the finger.
 * Tap the scrim, drag it away, or press back to close.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SidebarModalDrawer(
    state: SidebarDrawerState,
    drawerContent: @Composable () -> Unit,
    content: @Composable () -> Unit,
    panelWidth: Dp = 320.dp,
    /**
     * True while a text field *inside the drawer* has focus (the sidebar hosts the API-token
     * field): BACK then dismisses the keyboard and leaves the panel open, instead of closing
     * it — and never falls through to the activity's own back handling.
     */
    editing: Boolean = false,
    /** What BACK does while [editing]; the host hides the keyboard. */
    onBackWhileEditing: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val panelWidthPx = with(LocalDensity.current) { panelWidth.toPx() }
    val borderColor = MaterialTheme.colorScheme.outline
    val imeVisible = WindowInsets.isImeVisible
    val gesturesEnabled = !imeVisible

    // The drawer always keeps the back gesture while it is open, so a stray BACK can never
    // finish the activity underneath. While a field has focus it just dismisses the keyboard.
    BackHandler(enabled = state.isOpen) {
        if (editing) onBackWhileEditing() else scope.launch { state.close() }
    }
    PredictiveBackHandler(enabled = state.isOpen && !editing) { progress ->
        try {
            progress.collect { event ->
                state.updateFraction(1f - event.progress.coerceIn(0f, 1f))
            }
            state.close()
        } catch (cancelled: CancellationException) {
            state.restore()
            throw cancelled
        }
    }

    val edgePx = with(LocalDensity.current) { EdgeDragWidth.toPx() }

    Box(Modifier.fillMaxSize()) {
        // Invisible marker along the left edge. It carries NO pointer input, so it can never
        // take a touch away from the content drawn above it (a sibling strip *with* a
        // draggable on it swallows taps on anything underneath — that is how a 44dp strip
        // once made the top-bar menu button stop working). Its only job is
        // `systemGestureExclusion()`: under gesture navigation the OS otherwise claims an
        // inward swipe from the edge as "back" and the drawer never sees it.
        if (!state.isOpen && gesturesEnabled) {
            Box(
                modifier = Modifier
                    .width(EdgeDragWidth)
                    .fillMaxHeight()
                    .systemGestureExclusion(),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                // Opening the drawer by dragging from the left edge. This lives on the content
                // wrapper — a *parent* of the top bar — because a parent is dispatched after
                // its children: taps still reach the menu button, while a drag that starts
                // inside the edge strip and moves right opens the panel.
                .then(
                    if (gesturesEnabled) {
                        Modifier.pointerInput(gesturesEnabled) {
                            // The node stays alive for the whole open animation (this
                            // pointerInput is deliberately not keyed on `state.isOpen`):
                            // cancelling it mid-settle would abort the animation and leave the
                            // panel stuck at the drag fraction.
                            while (true) {
                                val velocity = awaitPointerEventScope { dragDrawerFromEdge(state, edgePx) }
                                if (velocity != null) state.settle(velocity)
                            }
                        }
                    } else {
                        Modifier
                    },
                )
                .graphicsLayer {
                    scaleX = state.contentScale
                    scaleY = state.contentScale
                    rotationY = state.contentRotationY
                    cameraDistance = 14f * density
                    translationX = state.contentOffsetPx
                    clip = state.fraction > 0.001f
                    shape = RoundedCornerShape(state.contentCornerPx)
                }
                .drawWithContent {
                    drawContent()
                    if (state.fraction > 0.001f) {
                        drawRoundRect(
                            color = borderColor.copy(alpha = 0.65f * state.fraction),
                            cornerRadius = CornerRadius(state.contentCornerPx),
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    }
                },
        ) {
            content()
            // the wash sits inside the clipped layer, so it only lightens the card itself
            if (state.scrimAlpha > 0f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = state.scrimAlpha)),
                )
            }
        }

        if (state.fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta -> state.dragBy(delta) },
                        onDragStopped = { velocity -> state.settle(velocity) },
                        enabled = gesturesEnabled,
                    )
                    .clickable { scope.launch { state.close() } },
            )
        }

        Box(
            modifier = Modifier
                .width(panelWidth)
                .fillMaxHeight()
                .graphicsLayer { translationX = -panelWidthPx * (1f - state.fraction) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta -> state.dragBy(delta) },
                    onDragStopped = { velocity -> state.settle(velocity) },
                    enabled = gesturesEnabled,
                ),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxSize(),
            ) {
                drawerContent()
            }
        }

    }
}

/** Landscape / wide-screen variant: the sidebar sits next to the content. */
@Composable
fun SidebarDockedDrawer(
    drawerContent: @Composable () -> Unit,
    content: @Composable () -> Unit,
    panelWidth: Dp = 300.dp,
) {
    Row(Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .width(panelWidth)
                .fillMaxHeight(),
        ) {
            drawerContent()
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
        ) {
            content()
        }
    }
}

/** Picks the docked variant on wide screens; a tablet keeps the sidebar visible. */
@Composable
fun SidebarDrawerHost(
    state: SidebarDrawerState,
    drawerContent: @Composable () -> Unit,
    content: @Composable () -> Unit,
    moveContent: Boolean = true,
    editing: Boolean = false,
    onBackWhileEditing: () -> Unit = {},
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        LaunchedEffect(moveContent) { state.moveContent = moveContent }
        val panelWidth = minOf(360.dp, maxWidth * 0.84f)
        val density = LocalDensity.current
        val panelWidthPx = with(density) { panelWidth.toPx() }
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val deviceRadius = rememberDeviceCornerRadius()
        val cornerPx = with(density) { deviceRadius.toPx() }
        val fallbackCornerPx = with(density) { 28.dp.toPx() }
        LaunchedEffect(panelWidthPx, cornerPx) {
            state.updatePanelWidth(panelWidthPx)
            state.updateShift(panelWidthPx + cornerPx)
        }
        LaunchedEffect(screenWidthPx) { state.updateMetrics(screenWidthPx, cornerPx) }
        LaunchedEffect(cornerPx, fallbackCornerPx) {
            state.useDeviceCornerRadius(cornerPx, fallbackCornerPx)
        }
        if (maxWidth >= 600.dp) {
            SidebarDockedDrawer(
                drawerContent = drawerContent,
                content = content,
                panelWidth = minOf(300.dp, panelWidth),
            )
        } else {
            SidebarModalDrawer(
                state = state,
                drawerContent = drawerContent,
                content = content,
                panelWidth = panelWidth,
                editing = editing,
                onBackWhileEditing = onBackWhileEditing,
            )
        }
    }
}
