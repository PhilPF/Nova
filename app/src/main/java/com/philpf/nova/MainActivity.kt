package com.philpf.nova

import android.content.Intent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.util.Rational
import android.view.KeyEvent
import androidx.compose.runtime.CompositionLocalProvider
import com.philpf.nova.ui.LocalPip
import com.philpf.nova.ui.PipState
import com.philpf.nova.widget.NowPlayingWidget
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.philpf.nova.device.ThemeMode
import com.philpf.nova.ui.DemoScript
import com.philpf.nova.ui.NovaScreen
import com.philpf.nova.ui.PlayerConnection
import com.philpf.nova.ui.theme.NovaTheme

class MainActivity : ComponentActivity() {
    private val app: NovaApp get() = application as NovaApp
    private lateinit var connection: PlayerConnection
    private var openPlayerTick by mutableIntStateOf(0)
    private val pip = PipState()
    private var pipParams: PictureInPictureParams? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Debug builds can show a sample library, for screenshots: --ez demo true
        // [--es theme light|dark] [--es play mix|album] [--es open player|art|fullscreen|mix|album|artist|tracklist|settings|menu|queue]
        // [--es tab songs|albums|artists|playlists]
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val demo = debuggable && intent.getBooleanExtra("demo", false)
        if (demo) app.enableDemo()
        val forcedTheme = if (demo) intent.getStringExtra("theme")?.let { n -> ThemeMode.entries.firstOrNull { it.name.equals(n, true) } } else null
        val script = if (demo) DemoScript(intent.getStringExtra("open"), intent.getStringExtra("play"), intent.getStringExtra("tab")) else null
        if (intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)) openPlayerTick++

        connection = PlayerConnection(this)
        pip.available = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        pip.onUpdate = { w, h, autoEnter, playing ->
            val params = pipParams(w, h, autoEnter, playing)
            pipParams = params
            runCatching { setPictureInPictureParams(params) }
        }
        pip.onEnter = {
            runCatching { enterPictureInPictureMode(pipParams ?: pipParams(16, 9, false, true)) }
        }
        setContent {
            val stored by app.settings.theme.collectAsState()
            val dark = when (forcedTheme ?: stored) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SideEffect {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            NovaTheme(dark = dark) {
                CompositionLocalProvider(LocalPip provides pip) {
                    NovaScreen(app, connection, openPlayerTick, script)
                }
            }
        }
    }

    /**
     * The small window's shape (Android allows from 1:2.39 to 2.39:1) and its buttons, which
     * are media buttons to the playback service, as the widget's.
     */
    private fun pipParams(w: Int, h: Int, autoEnter: Boolean, playing: Boolean): PictureInPictureParams {
        val ratio = (w.toFloat() / h.coerceAtLeast(1)).coerceIn(1f / 2.39f, 2.39f)
        val shape = if (ratio >= 1f) Rational((ratio * 1000).toInt(), 1000) else Rational(1000, (1000 / ratio).toInt())
        fun action(icon: Int, title: String, key: Int) = RemoteAction(
            Icon.createWithResource(this, icon), title, title, NowPlayingWidget.mediaButton(this, key),
        )
        return PictureInPictureParams.Builder()
            .setAspectRatio(shape)
            .setAutoEnterEnabled(autoEnter)
            .setSeamlessResizeEnabled(true)
            .setActions(
                listOf(
                    action(R.drawable.ic_widget_previous, "Previous", KeyEvent.KEYCODE_MEDIA_PREVIOUS),
                    if (playing) action(R.drawable.ic_pip_pause, "Pause", KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                    else action(R.drawable.ic_pip_play, "Play", KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE),
                    action(R.drawable.ic_widget_next, "Next", KeyEvent.KEYCODE_MEDIA_NEXT),
                ),
            )
            .build()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pip.active = isInPictureInPictureMode
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)) openPlayerTick++
    }

    override fun onStart() {
        super.onStart()
        connection.connect()
    }

    override fun onStop() {
        connection.release()
        super.onStop()
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "openPlayer"
    }
}
