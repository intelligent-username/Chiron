package com.chiron.feature.timer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import kotlin.math.roundToInt

internal const val StatsMotionMillis = 180

/** Layers belong to the host, not the disposable destinations. No retained composition/input. */
internal class StatsGraphMotionState(
    private val graphics: GraphicsContext,
    private val scope: CoroutineScope,
    initialMode: Int
) {
    private val layers = mutableListOf<GraphicsLayer>()
    private fun layer() = graphics.createGraphicsLayer().also { layers += it }
    var host: LayoutCoordinates? = null
    var mode by mutableStateOf(initialMode)
        private set
    var generation by mutableStateOf(0)
        private set
    private var live = layer()
    private var liveBounds: Rect? = null
    private var liveCoordinates: LayoutCoordinates? = null
    private var liveVisible = false
    private var recorded = false
    private var source: GraphicsLayer? = null
    private var sourceBounds: Rect? = null
    private var composite: GraphicsLayer? = null
    private var appliedBounds: Rect? = null
    private var lastPresentation: GraphicsLayer? = null
    private var lastPresentationBounds: Rect? = null
    private var job: Job? = null
    private var fraction = Animatable(1f)
    var moving by mutableStateOf(false)
        private set
    private var targetBounds by mutableStateOf<Rect?>(null)
    val visibleBounds: Rect?
        get() = if (moving) sourceBounds?.let { from ->
            targetBounds?.let { lerp(from, it, fraction.value) }
        } else liveBounds

    fun switchTo(next: Int) {
        if (next == mode) return
        val anchor = liveCoordinates
        if (anchor?.isAttached == true) measured(mode, anchor)
        else if (!moving) liveVisible = false
        // Freeze the last *drawn* composite. Each new destination gets a fresh layer,
        // so reversing cannot overwrite a layer referenced by the frozen recording.
        val origin = lastPresentationBounds
        val previous = lastPresentation.takeIf { liveVisible || moving || source != null }
        job?.cancel()
        fraction = Animatable(0f)
        source = previous
        sourceBounds = origin.takeIf { previous != null }
        moving = false
        targetBounds = null
        liveBounds = null
        liveCoordinates = null
        liveVisible = false
        recorded = false
        appliedBounds = null
        composite = null
        live = layer()
        mode = next
        generation++
    }

    fun measured(key: Int, coordinates: LayoutCoordinates) {
        val parent = host ?: return
        if (key != mode || !parent.isAttached || !coordinates.isAttached) return
        liveCoordinates = coordinates
        val rect = parent.localBoundingBoxOf(coordinates, clipBounds = false)
        val clipped = parent.localBoundingBoxOf(coordinates, clipBounds = true)
        if (moving) {
            // A global callback may include the graphics-layer matrix. Never feed
            // our own visual transform back into the destination layout rectangle.
            if ((rect.near(targetBounds) || rect.near(appliedBounds)) &&
                kotlin.math.abs(clipped.width - rect.width) < 1f &&
                kotlin.math.abs(clipped.height - rect.height) < 1f) return
            finish()
            liveBounds = null
            liveVisible = false
            recorded = false
            return
        }
        liveBounds = rect
        // Partial/offscreen Cards are deliberately not promoted above their scroll clip.
        liveVisible = rect.width > 0 && rect.height > 0 &&
            kotlin.math.abs(clipped.width - rect.width) < 1f &&
            kotlin.math.abs(clipped.height - rect.height) < 1f &&
            rect.left >= 0 && rect.top >= 0 &&
            rect.right <= parent.size.width && rect.bottom <= parent.size.height
        if (!liveVisible) finish()
    }

    fun record(key: Int, draw: androidx.compose.ui.graphics.drawscope.ContentDrawScope) {
        if (key != mode) {
            draw.drawContent()
            return
        }
        with(draw) { live.record { this@with.drawContent() } }
        recorded = true
        if (source != null && sourceBounds != null && liveVisible && !moving) {
            val destination = liveBounds ?: return
            targetBounds = destination
            composite = layer()
            moving = true
            val ticket = generation
            job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                fraction.animateTo(1f, tween(StatsMotionMillis))
                if (ticket == generation) finish()
            }
        }
        if (!overlayReady()) {
            // Until the layer matrix catches up, show the one live chart rather
            // than showing a recording over mismatched touch coordinates.
            with(draw) { drawLayer(live) }
            lastPresentation = live
            lastPresentationBounds = if (moving) appliedBounds ?: liveBounds else liveBounds
        }
    }

    fun transform(key: Int, block: androidx.compose.ui.graphics.GraphicsLayerScope) {
        with(block) {
            transformOrigin = TransformOrigin(0f, 0f)
            translationX = 0f
            translationY = 0f
            scaleX = 1f
            scaleY = 1f
        }
        if (key != mode || !moving) return
        val actual = targetBounds ?: return
        val shown = visibleBounds ?: return
        appliedBounds = shown
        with(block) {
            transformOrigin = TransformOrigin(0f, 0f)
            translationX = shown.left - actual.left
            translationY = shown.top - actual.top
            scaleX = shown.width / actual.width
            scaleY = shown.height / actual.height
        }
    }

    fun draw(scope: DrawScope) {
        if (!overlayReady()) return
        val rect = visibleBounds ?: return
        val outgoing = source ?: return
        val recording = composite ?: return
        val p = fraction.value
        with(scope) {
            recording.record(size = IntSize(rect.width.roundToInt().coerceAtLeast(1),
                rect.height.roundToInt().coerceAtLeast(1))) {
                val outline = Path().apply { addRoundRect(RoundRect(
                    Rect(Offset.Zero, size), androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()))) }
                clipPath(outline) {
                    scaledRecording(outgoing)
                    drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply { alpha = p })
                    scaledRecording(live)
                    drawContext.canvas.restore()
                }
            }
            lastPresentation = recording
            lastPresentationBounds = rect
            translate(rect.left, rect.top) { drawLayer(recording) }
        }
    }

    private fun overlayReady(): Boolean = moving && visibleBounds?.near(appliedBounds) == true

    fun abandonUnmeasured(ticket: Int) {
        if (ticket == generation && (!recorded || !liveVisible)) finish()
    }

    private fun finish() {
        job?.cancel()
        job = null
        moving = false
        source = null
        sourceBounds = null
        composite = null
        val ticket = generation
        scope.launch {
            withFrameNanos { }
            withFrameNanos { }
            if (ticket == generation && !moving) {
                val obsolete = layers.filter { it !== live }
                if (obsolete.any { it === lastPresentation }) {
                    lastPresentation = null
                    lastPresentationBounds = null
                }
                obsolete.forEach(graphics::releaseGraphicsLayer)
                layers.removeAll(obsolete.toSet())
            }
        }
    }

    fun dispose() {
        job?.cancel()
        layers.forEach(graphics::releaseGraphicsLayer)
        layers.clear()
    }
}

private fun Rect.near(other: Rect?): Boolean = other != null &&
    kotlin.math.abs(left - other.left) < 1f && kotlin.math.abs(top - other.top) < 1f &&
    kotlin.math.abs(right - other.right) < 1f && kotlin.math.abs(bottom - other.bottom) < 1f

private fun DrawScope.scaledRecording(layer: GraphicsLayer) {
    if (layer.size.width <= 0 || layer.size.height <= 0) return
    scale(size.width / layer.size.width, size.height / layer.size.height, Offset.Zero) {
        drawLayer(layer)
    }
}

@Composable
internal fun rememberStatsGraphMotionState(initialMode: Int = 0): StatsGraphMotionState {
    val graphics = LocalGraphicsContext.current
    val scope = rememberCoroutineScope()
    val state = remember(graphics, scope) { StatsGraphMotionState(graphics, scope, initialMode) }
    DisposableEffect(state) { onDispose { state.dispose() } }
    val ticket = state.generation
    LaunchedEffect(state, ticket) {
        // Lazy graph offscreen/uncomposed: skip rather than scroll to fabricate a target.
        withFrameNanos { }
        withFrameNanos { }
        state.abandonUnmeasured(ticket)
    }
    return state
}

internal fun Modifier.statsGraphAnchor(state: StatsGraphMotionState, modeKey: Int): Modifier =
    onGloballyPositioned { state.measured(modeKey, it) }
        // Compose's layer transform also maps pointer/semantics coordinates back
        // into the one live destination; the visual-only overlay has no handlers.
        .graphicsLayer { state.transform(modeKey, this) }
        .drawWithContent { state.record(modeKey, this) }

@Composable
internal fun StatsGraphOverlay(state: StatsGraphMotionState, modifier: Modifier = Modifier) {
    Canvas(modifier) { state.draw(this) }
}

@Composable
internal fun statsPresentedNumber(value: Double, identity: Any): Double {
    val animation = remember(identity) { Animatable(value.toFloat()) }
    LaunchedEffect(animation, value) { animation.animateTo(value.toFloat(), tween(StatsMotionMillis)) }
    return if (animation.value == value.toFloat()) value else animation.value.toDouble()
}

/**
 * Animate only a caller's explicit mode/row-id change. Later measurement changes
 * (validation, suggestions, text wrapping) are added immediately, not retargeted.
 */
@Composable
internal fun StatsEditorGeometry(
    identity: Any,
    enabled: Boolean = true,
    resetKey: Any = Unit,
    content: @Composable () -> Unit
) {
    val geometry = remember(resetKey) { EditorGeometry(identity) }
    if (geometry.identity != identity) {
        geometry.origin = geometry.last
        geometry.target = 0
        geometry.changing = enabled && geometry.last > 0
        geometry.identity = identity
    }
    val progress = remember(identity, resetKey) { Animatable(if (geometry.changing) 0f else 1f) }
    LaunchedEffect(identity, enabled, resetKey) {
        if (geometry.changing && enabled) {
            progress.animateTo(1f, tween(StatsMotionMillis))
        }
        geometry.changing = false
    }
    Column(Modifier.clipToBounds().layout { measurable, constraints ->
        val measured = measurable.measure(constraints)
        if (geometry.changing && geometry.target == 0) geometry.target = measured.height
        val delta = if (geometry.changing && enabled)
            ((geometry.origin - geometry.target) * (1f - progress.value)).roundToInt() else 0
        val height = constraints.constrainHeight((measured.height + delta).coerceAtLeast(0))
        geometry.last = height
        layout(measured.width, height) { measured.placeRelative(0, 0) }
    }) { content() }
}

@Composable
internal fun StatsFoodRowGeometry(
    present: Boolean,
    initiallyPresent: Boolean,
    content: @Composable () -> Unit
) {
    val fraction = remember { Animatable(if (initiallyPresent) 1f else 0f) }
    val geometry = remember { EditorGeometry(Unit) }
    LaunchedEffect(present) {
        fraction.animateTo(if (present) 1f else 0f, tween(StatsMotionMillis))
    }
    Column(Modifier.clipToBounds().layout { measurable, constraints ->
        val child = measurable.measure(constraints)
        if (present) geometry.target = child.height
        val height = constraints.constrainHeight((geometry.target * fraction.value).roundToInt())
        layout(child.width, height) { if (present) child.placeRelative(0, 0) }
    }.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

private class EditorGeometry(var identity: Any) {
    var last = 0
    var origin = 0
    var target = 0
    var changing = false
}
