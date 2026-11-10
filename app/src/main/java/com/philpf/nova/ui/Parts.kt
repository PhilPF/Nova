package com.philpf.nova.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.philpf.nova.core.Chapters
import com.philpf.nova.core.Track
import com.philpf.nova.ui.art.Icon
import com.philpf.nova.ui.art.NovaIcon
import com.philpf.nova.ui.theme.GlassType
import com.philpf.nova.ui.theme.LocalGlass

/** A song in a list: its picture, title and artist, its length and a button for more. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: Track,
    playing: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    number: String? = null,
) {
    val glass = LocalGlass.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (playing) glass.controlFillSelected else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onMore)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (number != null) {
            Text(number, style = GlassType.callout, color = glass.textTertiary, modifier = Modifier.width(26.dp))
        } else {
            Artwork(track.file.art, size = 48.dp, video = track.file.isVideo)
        }
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = GlassType.headline,
                color = if (playing) glass.accent else glass.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val line = subtitle ?: listOfNotNull(track.artist, if (track.file.isMix && !track.isClip) "${track.file.chapters.size} songs" else null)
                .joinToString(" · ")
            if (line.isNotEmpty()) {
                Text(line, style = GlassType.caption, color = glass.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (track.file.isVideo) Icon(NovaIcon.VIDEO, glass.textTertiary, Modifier.size(16.dp))
        Text(Chapters.formatTime(track.durationMs), style = GlassType.caption, color = glass.textTertiary)
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClickLabel = "More", onClick = onMore)
                .semantics { contentDescription = "More" },
            contentAlignment = Alignment.Center,
        ) { Icon(NovaIcon.MORE, glass.textSecondary, Modifier.size(20.dp)) }
    }
}

/** A row of a sheet's menu: an icon and what it does. */
@Composable
fun ActionRow(icon: NovaIcon, label: String, onClick: () -> Unit, enabled: Boolean = true, danger: Boolean = false) {
    val glass = LocalGlass.current
    val color = if (danger) glass.danger else glass.textPrimary
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, color.copy(alpha = if (enabled) color.alpha else 0.35f), Modifier.size(22.dp))
        Text(label, style = GlassType.body, color = color.copy(alpha = if (enabled) color.alpha else 0.35f))
    }
}

/** A sheet's heading: a picture, a title and a line under it. */
@Composable
fun SheetHeader(title: String, subtitle: String?, art: String?, video: Boolean = false, round: Boolean = false) {
    val glass = LocalGlass.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Artwork(art, size = 72.dp, video = video, shape = if (round) CircleShape else RoundedCornerShape(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = GlassType.display.copy(fontSize = 24.sp, lineHeight = 28.sp), color = glass.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrEmpty()) {
                Text(subtitle, style = GlassType.callout, color = glass.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

/**
 * The position in a song, with a mark at the start of each song of a long video. Tapping or
 * dragging moves the thumb; the player seeks when the finger lifts.
 */
@Composable
fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    markers: List<Long>,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    trackColor: Color = LocalGlass.current.track,
    fillColor: Color = LocalGlass.current.accent,
    markerColor: Color = LocalGlass.current.textPrimary,
    showTimes: Boolean = true,
    timeColor: Color = LocalGlass.current.textSecondary,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val latestSeek by rememberUpdatedState(onSeek)
    val latestDuration by rememberUpdatedState(durationMs)
    val fraction = if (dragging) dragFraction else if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val inset = 10.dp

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val f = ((offset.x - inset.toPx()) / (size.width - 2 * inset.toPx())).coerceIn(0f, 1f)
                        latestSeek((f * latestDuration).toLong())
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            dragFraction = ((offset.x - inset.toPx()) / (size.width - 2 * inset.toPx())).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            latestSeek((dragFraction * latestDuration).toLong())
                            dragging = false
                        },
                        onDragCancel = { dragging = false },
                    ) { change, _ ->
                        change.consume()
                        dragFraction = ((change.position.x - inset.toPx()) / (size.width - 2 * inset.toPx())).coerceIn(0f, 1f)
                    }
                }
                .semantics {
                    contentDescription = "Position"
                    stateDescription = Chapters.formatTime(positionMs)
                },
        ) {
            val left = inset.toPx()
            val width = size.width - 2 * left
            val h = (if (dragging) 8.dp else 5.dp).toPx()
            val y = size.height / 2f
            drawRoundRect(trackColor, Offset(left, y - h / 2), Size(width, h), CornerRadius(h / 2))
            drawRoundRect(fillColor, Offset(left, y - h / 2), Size(width * fraction, h), CornerRadius(h / 2))
            if (durationMs > 0) {
                for (m in markers) {
                    if (m <= 0 || m >= durationMs) continue
                    val x = left + width * (m.toFloat() / durationMs)
                    drawLine(markerColor.copy(alpha = 0.7f), Offset(x, y - h / 2 - 3.dp.toPx()), Offset(x, y + h / 2 + 3.dp.toPx()), strokeWidth = 2.dp.toPx())
                }
            }
            drawCircle(Color.White, radius = (if (dragging) 9.dp else 7.dp).toPx(), center = Offset(left + width * fraction, y))
        }
        if (showTimes) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                val shown = if (dragging) (dragFraction * durationMs).toLong() else positionMs
                Text(Chapters.formatTime(shown), style = GlassType.caption, color = timeColor, modifier = Modifier.weight(1f))
                Text("-" + Chapters.formatTime((durationMs - shown).coerceAtLeast(0)), style = GlassType.caption, color = timeColor)
            }
        }
    }
}

/** The player's video, fitted inside its space with black bars. Only one may be shown at a time. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoSurface(player: Player?, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                setKeepContentOnPlayerReset(true)
                this.player = player
            }
        },
        update = { if (it.player !== player) it.player = player },
        onRelease = { it.player = null },
        modifier = modifier.background(Color.Black),
    )
}

/** A big round play/pause button, filled with the accent. */
@Composable
fun PlayButton(playing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 72.dp, fill: Color = LocalGlass.current.textPrimary, iconColor: Color = LocalGlass.current.backgroundBottom) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(fill)
            .clickable(onClickLabel = if (playing) "Pause" else "Play", onClick = onClick)
            .semantics { contentDescription = if (playing) "Pause" else "Play" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (playing) NovaIcon.PAUSE else NovaIcon.PLAY, iconColor, Modifier.size(size * 0.5f))
    }
}

/** An icon that is a button, without a fill: the player's secondary controls. */
@Composable
fun PlainIconButton(icon: NovaIcon, label: String, onClick: () -> Unit, tint: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 48.dp, iconSize: androidx.compose.ui.unit.Dp = 26.dp, enabled: Boolean = true) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, tint.copy(alpha = if (enabled) tint.alpha else 0.3f), Modifier.size(iconSize)) }
}

/** Bold section text inside a sheet. */
@Composable
fun SheetTitle(text: String) {
    Text(text, style = GlassType.display.copy(fontSize = 26.sp, lineHeight = 30.sp), color = LocalGlass.current.textPrimary, modifier = Modifier.padding(vertical = 8.dp))
}
