package com.philpf.nova

import android.content.Intent
import android.content.pm.ApplicationInfo
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
                NovaScreen(app, connection, openPlayerTick, script)
            }
        }
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
