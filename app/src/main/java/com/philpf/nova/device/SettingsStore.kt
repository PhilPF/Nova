package com.philpf.nova.device

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** The app's settings, in SharedPreferences. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(prefs.getString(KEY_THEME, null)?.let { n -> ThemeMode.entries.firstOrNull { it.name == n } } ?: ThemeMode.SYSTEM)
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    /** The music folders, as tree URIs from the Storage Access Framework. */
    private val _folders = MutableStateFlow(prefs.getStringSet(KEY_FOLDERS, emptySet()).orEmpty().sorted())
    val folders: StateFlow<List<String>> = _folders.asStateFlow()

    /** Whether a video plays as video in the player (else its album art is shown). */
    private val _showVideo = MutableStateFlow(prefs.getBoolean(KEY_SHOW_VIDEO, true))
    val showVideo: StateFlow<Boolean> = _showVideo.asStateFlow()

    fun setTheme(mode: ThemeMode) {
        _theme.value = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    fun setFolders(list: List<String>) {
        _folders.value = list.distinct().sorted()
        prefs.edit().putStringSet(KEY_FOLDERS, list.toSet()).apply()
    }

    fun setShowVideo(on: Boolean) {
        _showVideo.value = on
        prefs.edit().putBoolean(KEY_SHOW_VIDEO, on).apply()
    }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_FOLDERS = "folders"
        const val KEY_SHOW_VIDEO = "showVideo"
    }
}
