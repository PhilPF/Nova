package com.philpf.nova.core

/** What a double tap on the player's picture does. */
sealed interface SkipAction {
    /** Seek to this position in the song (from its start). */
    data class Seek(val ms: Long) : SkipAction

    /** To the start of the next song. */
    data object Next : SkipAction

    /** To the start of the previous song. */
    data object Previous : SkipAction
}

/**
 * Double tap to skip: [STEP_MS] forward on the right of the picture, back on the left.
 * Forward past the end of the song (or within [END_MARGIN_MS] of it) goes to the next song.
 * Back past its start by up to [GRACE] of a step goes to its start (a tap "a little too far"
 * still means this song); further back, to the previous song's start.
 */
object SkipRule {
    const val STEP_MS = 10_000L
    const val GRACE = 0.3
    const val END_MARGIN_MS = 500L

    /**
     * [positionMs] and [durationMs] are within the song (for a song inside a long video, from
     * its own start); a duration of 0 or less is unknown.
     */
    fun of(positionMs: Long, durationMs: Long, deltaMs: Long, hasNext: Boolean, hasPrevious: Boolean): SkipAction {
        val target = positionMs + deltaMs
        if (deltaMs >= 0) {
            if (durationMs > 0 && target >= durationMs - END_MARGIN_MS) {
                return if (hasNext) SkipAction.Next else SkipAction.Seek(durationMs)
            }
            return SkipAction.Seek(target)
        }
        return when {
            target >= 0 -> SkipAction.Seek(target)
            target >= -(GRACE * -deltaMs).toLong() -> SkipAction.Seek(0)
            hasPrevious -> SkipAction.Previous
            else -> SkipAction.Seek(0)
        }
    }
}
