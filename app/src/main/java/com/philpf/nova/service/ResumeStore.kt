package com.philpf.nova.service

import android.content.Context
import com.philpf.nova.core.Json

/**
 * The queue, where it was and how far in, kept so that playing again after the app was
 * closed (from the widget, headphones or the system's media controls) carries on.
 */
object ResumeStore {
    data class Saved(val ids: List<String>, val index: Int, val positionMs: Long)

    private const val PREFS = "resume"

    fun save(context: Context, ids: List<String>, index: Int, positionMs: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("ids", Json.write(ids)).putInt("index", index).putLong("position", positionMs)
            .apply()
    }

    fun savePosition(context: Context, index: Int, positionMs: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("index", index).putLong("position", positionMs).apply()
    }

    fun load(context: Context): Saved? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ids = (runCatching { Json.parse(p.getString("ids", null) ?: return null) }.getOrNull() as? List<*>)
            ?.filterIsInstance<String>()?.takeIf { it.isNotEmpty() } ?: return null
        return Saved(ids, p.getInt("index", 0).coerceIn(ids.indices), p.getLong("position", 0))
    }
}
