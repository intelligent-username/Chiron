package com.chiron.app.spotify

/**
 * Polymorphic time formatter interface for playback positions and durations.
 */
sealed interface PlaybackTimeFormatter {
    fun format(ms: Float): String
    fun format(ms: Long): String = format(ms.toFloat())
    fun format(ms: Int): String = format(ms.toFloat())

    companion object {
        private const val ONE_HOUR_MS = 3_600_000f

        /**
         * Returns the appropriate polymorphic formatter based on total media duration.
         */
        fun forDuration(durationMs: Float): PlaybackTimeFormatter {
            return if (durationMs >= ONE_HOUR_MS) {
                HourMinuteSecondFormatter
            } else {
                MinuteSecondFormatter
            }
        }

        fun forDuration(durationMs: Long): PlaybackTimeFormatter = forDuration(durationMs.toFloat())

        /**
         * Adaptive formatter that chooses h:mm:ss vs m:ss per individual timestamp.
         */
        fun adaptive(): PlaybackTimeFormatter = AdaptiveTimeFormatter
    }
}

/** Formatter for media under 1 hour (< 60 mins): "m:ss". Gracefully shows hours if an unexpected overflow occurs. */
object MinuteSecondFormatter : PlaybackTimeFormatter {
    override fun format(ms: Float): String {
        val totalSec = (ms / 1000).toLong().coerceAtLeast(0)
        val hours = totalSec / 3600
        val mins = (totalSec % 3600) / 60
        val secs = totalSec % 60
        return if (hours > 0) {
            "$hours:${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
        } else {
            "$mins:${secs.toString().padStart(2, '0')}"
        }
    }
}

/** Formatter for media 1 hour or longer (>= 60 mins): "h:mm:ss" (e.g. 1:08:31, 2:10:00). */
object HourMinuteSecondFormatter : PlaybackTimeFormatter {
    override fun format(ms: Float): String {
        val totalSec = (ms / 1000).toLong().coerceAtLeast(0)
        val hours = totalSec / 3600
        val mins = (totalSec % 3600) / 60
        val secs = totalSec % 60
        return "$hours:${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
    }
}

/** Polymorphic formatter that dynamically displays h:mm:ss when hours > 0, otherwise m:ss. */
object AdaptiveTimeFormatter : PlaybackTimeFormatter {
    override fun format(ms: Float): String {
        val totalSec = (ms / 1000).toLong().coerceAtLeast(0)
        val hours = totalSec / 3600
        val mins = (totalSec % 3600) / 60
        val secs = totalSec % 60
        return if (hours > 0) {
            "$hours:${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
        } else {
            "$mins:${secs.toString().padStart(2, '0')}"
        }
    }
}
