package com.philpf.nova.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.SizeF
import android.view.KeyEvent
import android.view.View
import android.widget.RemoteViews
import com.philpf.nova.MainActivity
import com.philpf.nova.R
import com.philpf.nova.service.PlaybackService

/**
 * The home-screen widget: the song playing with its art. Square, the art fills it with the
 * song over its foot and a play button; wide, the art is blurred behind (as the app's glass
 * would, which a widget cannot draw) with the art, the song, and previous, play and next.
 * The playback service keeps it up to date; its buttons are media buttons, as headphones send.
 */
class NowPlayingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, load(context), ids)
    }

    /** What the widget shows. [art] is a picture file of the app's art cache. */
    data class Info(val title: String?, val artist: String?, val art: String?, val playing: Boolean, val hasMedia: Boolean)

    companion object {
        private const val PREFS = "widget"

        /** Shows [info] on every widget (and keeps it for when the launcher asks). Call off the main thread. */
        fun update(context: Context, info: Info) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("title", info.title).putString("artist", info.artist).putString("art", info.art)
                .putBoolean("playing", info.playing).putBoolean("hasMedia", info.hasMedia)
                .apply()
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NowPlayingWidget::class.java))
            if (ids.isNotEmpty()) render(context, info, ids)
        }

        private fun load(context: Context): Info {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            // The service is not running when the launcher asks: shown paused.
            return Info(p.getString("title", null), p.getString("artist", null), p.getString("art", null), false, p.getBoolean("hasMedia", false))
        }

        private fun render(context: Context, info: Info, ids: IntArray) {
            val art = info.art?.let { decode(it, 360) }
            val blurred = art?.let(::blur)
            val views = RemoteViews(
                mapOf(
                    SizeF(110f, 80f) to small(context, info, art),
                    SizeF(220f, 80f) to wide(context, info, art, blurred),
                ),
            )
            AppWidgetManager.getInstance(context).updateAppWidget(ids, views)
        }

        /** Both layouts for [info], as the widget shows them (for the debug build's preview). */
        fun preview(context: Context, info: Info): List<RemoteViews> {
            val art = info.art?.let { decode(it, 360) }
            return listOf(small(context, info, art), wide(context, info, art, art?.let(::blur)))
        }

        private fun small(context: Context, info: Info, art: Bitmap?) = RemoteViews(context.packageName, R.layout.widget_small).apply {
            common(context, info, art)
        }

        private fun wide(context: Context, info: Info, art: Bitmap?, blurred: Bitmap?) = RemoteViews(context.packageName, R.layout.widget_wide).apply {
            common(context, info, art)
            if (blurred != null) setImageViewBitmap(R.id.widget_backdrop, blurred) else setImageViewBitmap(R.id.widget_backdrop, null)
            setOnClickPendingIntent(R.id.widget_previous, mediaButton(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
            setOnClickPendingIntent(R.id.widget_next, mediaButton(context, KeyEvent.KEYCODE_MEDIA_NEXT))
            val visibility = if (info.hasMedia) View.VISIBLE else View.INVISIBLE
            setViewVisibility(R.id.widget_previous, visibility)
            setViewVisibility(R.id.widget_next, visibility)
        }

        private fun RemoteViews.common(context: Context, info: Info, art: Bitmap?) {
            setTextViewText(R.id.widget_title, info.title ?: "Nova")
            setTextViewText(R.id.widget_artist, if (info.hasMedia) info.artist.orEmpty() else "Nothing playing")
            setImageViewBitmap(R.id.widget_art, art)
            setViewVisibility(R.id.widget_empty, if (art == null) View.VISIBLE else View.GONE)
            setImageViewResource(R.id.widget_play, if (info.playing) R.drawable.ic_widget_pause_dark else R.drawable.ic_widget_play_dark)
            setContentDescription(R.id.widget_play, if (info.playing) "Pause" else "Play")
            setOnClickPendingIntent(R.id.widget_root, openPlayer(context))
            // With nothing to resume, Play opens the app.
            setOnClickPendingIntent(R.id.widget_play, if (info.hasMedia) mediaButton(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) else openPlayer(context))
        }

        private fun openPlayer(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_PLAYER, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** A media button sent to the playback service, which hands it to its session. */
        private fun mediaButton(context: Context, keyCode: Int): PendingIntent = PendingIntent.getService(
            context, keyCode,
            Intent(Intent.ACTION_MEDIA_BUTTON)
                .setComponent(ComponentName(context, PlaybackService::class.java))
                .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun decode(path: String, size: Int): Bitmap? = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= size) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()

        /** A soft blur: the picture shrunk to a few pixels and smoothly enlarged again. */
        private fun blur(b: Bitmap): Bitmap {
            val tiny = Bitmap.createScaledBitmap(b, 12, 12, true)
            val mid = Bitmap.createScaledBitmap(tiny, 48, 48, true)
            return Bitmap.createScaledBitmap(mid, 240, 240, true)
        }
    }
}
