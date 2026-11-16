package com.philpf.nova.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Picture in picture: the video in a small window over other apps. The activity does the work
 * (it owns the window); the screens say when a video is showing, and enter it from a button.
 */
@Stable
class PipState {
    /** Whether the device offers it. */
    var available by mutableStateOf(false)
        internal set

    /** Whether the app is in its small window now: then only the video is drawn. */
    var active by mutableStateOf(false)
        internal set

    internal var onEnter: () -> Unit = {}
    internal var onUpdate: (width: Int, height: Int, autoEnter: Boolean, playing: Boolean) -> Unit = { _, _, _, _ -> }

    fun enter() = onEnter()

    /**
     * The video's shape, whether leaving the app while it shows should go to picture in picture
     * ([autoEnter]: a video showing and playing), and whether it plays (for the window's button).
     */
    fun update(width: Int, height: Int, autoEnter: Boolean, playing: Boolean) = onUpdate(width, height, autoEnter, playing)
}

val LocalPip = staticCompositionLocalOf { PipState() }
