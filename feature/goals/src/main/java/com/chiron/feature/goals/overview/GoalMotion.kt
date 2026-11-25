package com.chiron.feature.goals

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import com.chiron.core.ui.theme.ThinOutline
import kotlinx.coroutines.flow.first

/** Host-space visuals only: no outgoing composition, focus, input, or saved-state owner. */
internal data class GoalGeometry(
    val card: Rect,
    val ring: Rect,
    val radius: Float,
    val stroke: Float,
    val progress: Float,
    val accent: Color,
    val borderAlpha: Float
)

class GoalMotionState internal constructor() {
    var host: LayoutCoordinates? = null
    private val cards = mutableMapOf<Pair<Long, Boolean>, Rect>()
    private val rings = mutableMapOf<Pair<Long, Boolean>, Rect>()
    private val geometries = mutableMapOf<Pair<Long, Boolean>, GoalGeometry>()
    var request by mutableStateOf(0)
        private set
    internal var origin by mutableStateOf<GoalGeometry?>(null)
        private set
    internal var target by mutableStateOf<GoalGeometry?>(null)
        private set
    private var destination: Pair<Long, Boolean>? = null
    val fraction = Animatable(1f)
    private var runningGeneration by mutableStateOf(-1)
    var active by mutableStateOf(false)
        private set

    fun hides(id: Long) = active && destination?.first == id
    fun destinationAlpha(id: Long, detail: Boolean): Float {
        if (!active || destination != (id to detail)) return 1f
        if (runningGeneration != request) return 0f
        return ((fraction.value - 0.7f) / 0.3f).coerceIn(0f, 1f)
    }

    private fun visible(rect: Rect): Boolean {
        val size = host?.takeIf { it.isAttached }?.size ?: return false
        return rect.width > 0f && rect.height > 0f &&
            rect.left >= 0f && rect.top >= 0f &&
            rect.right <= size.width && rect.bottom <= size.height
    }

    fun begin(id: Long, fromDetail: Boolean) {
        val source = if (active && destination?.first == id) frame() else geometries[id to fromDetail]
        request++
        val key = id to !fromDetail
        destination = key
        // A stale grid measurement must never stand in for an uncomposed return target.
        cards.remove(key)
        rings.remove(key)
        geometries.remove(key)
        origin = source?.takeIf { visible(it.ring) }
        target = null
        active = origin != null
    }

    fun cancel() {
        request++
        active = false
        origin = null
        target = null
        destination = null
    }

    fun measure(
        id: Long,
        detail: Boolean,
        ring: Boolean,
        coordinates: LayoutCoordinates,
        radius: Float,
        stroke: Float,
        progress: Float,
        accent: Color
    ) {
        val hostCoordinates = host?.takeIf { it.isAttached } ?: return
        if (!coordinates.isAttached) return
        val key = id to detail
        val bounds = hostCoordinates.localBoundingBoxOf(coordinates, clipBounds = false)
        if (ring) rings[key] = bounds else cards[key] = bounds
        // Only the ring moves. Card and ring layout callbacks can arrive in either
        // order, so a card callback must not publish an old ring as the destination.
        if (!ring) return
        val donut = bounds
        val card = cards[key] ?: donut
        val geometry = GoalGeometry(card, donut, radius, stroke, progress, accent, if (detail) 0.5f else 1f)
        geometries[key] = geometry
        if (active && destination == key) {
            val previousTarget = target
            if (previousTarget == null) {
                if (visible(donut)) target = geometry else cancel()
            } else if (donut != previousTarget.ring) {
                if (visible(donut)) target = geometry else cancel()
            }
        }
    }

    internal fun frame(): GoalGeometry? {
        val from = origin ?: return null
        if (runningGeneration != request) return from
        val to = target ?: return from
        val t = fraction.value
        fun blend(a: Float, b: Float) = a + (b - a) * t
        fun rect(a: Rect, b: Rect) = Rect(
            blend(a.left, b.left), blend(a.top, b.top),
            blend(a.right, b.right), blend(a.bottom, b.bottom)
        )
        return GoalGeometry(
            rect(from.card, to.card), rect(from.ring, to.ring),
            blend(from.radius, to.radius), blend(from.stroke, to.stroke),
            blend(from.progress, to.progress), lerp(from.accent, to.accent, t),
            blend(from.borderAlpha, to.borderAlpha)
        )
    }

    fun finish(generation: Int) {
        if (request == generation) active = false
    }

    internal fun start(generation: Int) {
        runningGeneration = generation
    }
}

@Composable
internal fun rememberGoalMotionState(): GoalMotionState {
    val state = remember { GoalMotionState() }
    val generation = state.request
    LaunchedEffect(generation) {
        if (!state.active) return@LaunchedEffect
        // Destination anchors report during layout. Waiting a few frames instead of
        // cancelling after one avoids dropping the motion when the destination's first
        // composition lands late (the usual case on a cold goal-detail screen).
        var frames = 0
        while (state.request == generation && state.target == null && frames < 12) {
            withFrameNanos { }
            frames++
        }
        if (state.request == generation && state.target == null) state.cancel()
    }
    LaunchedEffect(generation) {
        if (!state.active) return@LaunchedEffect
        snapshotFlow { state.target }.first { it != null }
        // The destination's first composition/layout is the most expensive frame of the
        // transition. Let it finish before the tween clock starts; otherwise the whole
        // 180ms tween can be consumed inside that single long frame and render as a jump.
        withFrameNanos { }
        if (state.request != generation || state.target == null) return@LaunchedEffect
        state.fraction.snapTo(0f)
        state.start(generation)
        state.fraction.animateTo(1f, tween(durationMillis = 180, delayMillis = 0))
        state.finish(generation)
    }
    return state
}

internal fun Modifier.goalAnchor(
    state: GoalMotionState?,
    id: Long,
    detail: Boolean,
    ring: Boolean,
    radius: Float,
    stroke: Float,
    progress: Float,
    accent: Color
): Modifier = if (state == null) this else onGloballyPositioned {
    state.measure(id, detail, ring, it, radius, stroke, progress, accent)
}

/** Draw the shared ring above the destination without moving its interactive content. */
@Composable
internal fun GoalMotionOverlay(state: GoalMotionState) {
    val thinOutline = ThinOutline
    Canvas(Modifier.fillMaxSize()) {
        if (!state.active) return@Canvas
        val geometry = state.frame() ?: return@Canvas
        val ring = geometry.ring
        // Match GoalDonut's existing stroke-centered oval geometry.
        drawArc(
            thinOutline, -90f, 360f, false, ring.topLeft, ring.size,
            style = Stroke(geometry.stroke, cap = StrokeCap.Round)
        )
        drawArc(
            geometry.accent, -90f, 360f * geometry.progress.coerceIn(0f, 1f),
            false, ring.topLeft, ring.size,
            style = Stroke(geometry.stroke, cap = StrokeCap.Round)
        )
    }
}
