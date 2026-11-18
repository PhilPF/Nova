package com.philpf.nova.service

import android.content.Context
import com.philpf.nova.core.Json
import com.philpf.nova.core.QueueSource
import com.philpf.nova.core.SourceKind

/**
 * The queue, where it was and how far in, kept so that playing again after the app was
 * closed (from the widget, headphones or the system's media controls) carries on.
 */
object ResumeStore {
    data class Saved(val ids: List<String>, val sources: List<QueueSource?>, val index: Int, val positionMs: Long)

    private const val PREFS = "resume"

    fun save(context: Context, ids: List<String>, sources: List<QueueSource?>, index: Int, positionMs: Long) {
        val encoded = sources.map { s -> s?.let { mapOf("id" to it.id, "kind" to it.kind.name, "title" to it.title) } }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("ids", Json.write(ids)).putString("sources", Json.write(encoded))
            .putInt("index", index).putLong("position", positionMs)
            .apply()
    }

    fun savePosition(context: Context, index: Int, positionMs: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("index", index).putLong("position", positionMs).apply()
    }

    fun load(context: Context): Saved? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ids = (runCatching { Json.parse(p.getString("ids", null) ?: return null) }.getOrNull() as? List<*>)
            ?.filterIsInstance<String>()?.takeIf { it.isNotEmpty() } ?: return null
        val sources = (runCatching { Json.parse(p.getString("sources", null) ?: "[]") }.getOrNull() as? List<*>).orEmpty().map { m ->
            (m as? Map<*, *>)?.let {
                val id = it["id"] as? String ?: return@let null
                QueueSource(id, SourceKind.entries.firstOrNull { k -> k.name == it["kind"] } ?: SourceKind.LIST, it["title"] as? String ?: "")
            }
        }
        return Saved(ids, sources, p.getInt("index", 0).coerceIn(ids.indices), p.getLong("position", 0))
    }
}
