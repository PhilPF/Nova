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

    /** The music folder, as a tree URI from the Storage Access Framework (one at most). */
    private val _folder = MutableStateFlow(prefs.getString(KEY_FOLDER, null) ?: prefs.getStringSet(KEY_FOLDERS, emptySet()).orEmpty().firstOrNull())
    val folder: StateFlow<String?> = _folder.asStateFlow()

    /** Subfolders of the music folder left out of the library, as paths inside it ("Recordings/2024"). */
    private val _hidden = MutableStateFlow(prefs.getStringSet(KEY_HIDDEN, emptySet()).orEmpty().sorted())
    val hiddenFolders: StateFlow<List<String>> = _hidden.asStateFlow()

    /** Whether WhatsApp's and Telegram's audio and video are left out. */
    private val _skipMessaging = MutableStateFlow(prefs.getBoolean(KEY_SKIP_MESSAGING, true))
    val skipMessaging: StateFlow<Boolean> = _skipMessaging.asStateFlow()

    /** Whether a video plays as video in the player (else its album art is shown). */
    private val _showVideo = MutableStateFlow(prefs.getBoolean(KEY_SHOW_VIDEO, true))
    val showVideo: StateFlow<Boolean> = _showVideo.asStateFlow()

    fun setTheme(mode: ThemeMode) {
        _theme.value = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    fun setFolder(uri: String?) {
        _folder.value = uri
        prefs.edit().putString(KEY_FOLDER, uri).remove(KEY_FOLDERS).apply()
    }

    fun setHidden(paths: List<String>) {
        _hidden.value = paths.distinct().sorted()
        prefs.edit().putStringSet(KEY_HIDDEN, paths.toSet()).apply()
    }

    fun hideFolder(path: String) = setHidden(_hidden.value + path)

    fun showFolder(path: String) = setHidden(_hidden.value - path)

    fun setSkipMessaging(on: Boolean) {
        _skipMessaging.value = on
        prefs.edit().putBoolean(KEY_SKIP_MESSAGING, on).apply()
    }

    fun setShowVideo(on: Boolean) {
        _showVideo.value = on
        prefs.edit().putBoolean(KEY_SHOW_VIDEO, on).apply()
    }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_FOLDERS = "folders" // Older versions: a set of folders.
        const val KEY_FOLDER = "folder"
        const val KEY_HIDDEN = "hiddenFolders"
        const val KEY_SKIP_MESSAGING = "skipMessaging"
        const val KEY_SHOW_VIDEO = "showVideo"
    }
}
