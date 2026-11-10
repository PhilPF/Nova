package com.philpf.nova

import android.app.Application
import android.content.pm.ApplicationInfo
import com.philpf.nova.device.LibraryRepository
import com.philpf.nova.device.PlaylistStore
import com.philpf.nova.device.SettingsStore
import java.io.File

class NovaApp : Application() {
    val settings by lazy { SettingsStore(this) }
    val playlists by lazy { PlaylistStore(this) }
    val library by lazy { LibraryRepository(this, settings, demoDir) }

    /**
     * Debug builds started with "--ez demo true" read a sample library from a plain folder
     * (the app's own files, "Demo"), which the CI fills over adb with run-as for its screenshots.
     */
    var demoDir: File? = null
        private set

    fun enableDemo() {
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (debuggable) demoDir = File(filesDir, "Demo")
    }
}
