package com.philpf.nova.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.philpf.nova.ui.theme.GlassType
import com.philpf.nova.ui.theme.LocalGlass
import com.philpf.nova.ui.theme.contentColorOn
import kotlin.math.roundToInt

/** How faint a control is while it cannot be used (it keeps its place, so nothing moves). */
private const val DISABLED_ALPHA = 0.38f

/** Flat capsule button: a solid [accent] fill when [primary], translucent otherwise. */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    accent: Color = LocalGlass.current.accent,
    textColor: Color? = null,
    icon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    val glass = LocalGlass.current
    val fill = if (primary) accent else glass.controlFill
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(CircleShape)
            .background(fill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.invoke()
        Text(
            text = text,
            style = GlassType.headline,
            color = textColor ?: if (primary) contentColorOn(fill) else glass.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** Round translucent button holding an icon. */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(LocalGlass.current.controlFill)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = GlassType.section,
        color = LocalGlass.current.textTertiary,
        modifier = modifier.padding(start = 6.dp, bottom = 8.dp),
    )
}

/** The size of a colour swatch's touch area, and of the disc inside it. */
val SwatchSize = 48.dp
val SwatchDisc = 26.dp

/**
 * A light colour as a flat disc with a thin glass rim, like the panels. The selected one
 * lights up: it glows in its colour inside a thin ring.
 */
@Composable
fun Swatch(
    color: Color,
    isSelected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = LocalGlass.current
    val lit by animateFloatAsState(if (isSelected) 1f else 0f, tween(220), label = "swatch")
    Canvas(
        modifier = modifier
            .size(SwatchSize)
            .clip(CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = label
                selected = isSelected
            },
    ) {
        drawSelectionGlow(color, lit, glass.textPrimary)
        val r = SwatchDisc.toPx() / 2f
        drawCircle(color, radius = r)
        drawGlassRim(r, glass.isDark)
    }
}

/** The lit look of a selected swatch: a glow of [color] and a thin ring, faded by [lit]. */
fun DrawScope.drawSelectionGlow(color: Color, lit: Float, ring: Color) {
    if (lit <= 0f) return
    val outer = size.minDimension / 2f
    drawCircle(
        Brush.radialGradient(listOf(color.copy(alpha = 0.6f * lit), color.copy(alpha = 0f)), center, outer),
        radius = outer,
    )
    drawCircle(ring.copy(alpha = ring.alpha * lit), radius = SwatchDisc.toPx() / 2f + 5.dp.toPx(), style = Stroke(1.5.dp.toPx()))
}

/** A disc's glass finish: a faint sheen on its upper half and a thin rim. */
fun DrawScope.drawGlassRim(radius: Float, dark: Boolean) {
    drawCircle(
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.26f),
            0.55f to Color.White.copy(alpha = 0f),
            startY = center.y - radius,
            endY = center.y + radius,
        ),
        radius = radius,
    )
    val rim = if (dark) Color.White.copy(alpha = 0.32f) else Color(0xFF0E1222).copy(alpha = 0.14f)
    drawCircle(rim, radius = radius - 0.5.dp.toPx(), style = Stroke(1.dp.toPx()))
}

/** A selectable capsule with an optional colour dot, e.g. a player. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dot: Color? = null,
    onLongClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val glass = LocalGlass.current
    val fill by animateColorAsState(if (selected) glass.controlFillSelected else glass.controlFill, tween(180), label = "chip")
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(CircleShape)
            .background(fill)
            .then(if (selected) Modifier.border(1.dp, glass.textPrimary.copy(alpha = 0.18f), CircleShape) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics {
                this.selected = selected
                role = Role.RadioButton
            }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (dot != null) {
            Canvas(Modifier.size(10.dp)) { drawCircle(dot) }
        }
        leading?.invoke()
        if (text.isNotEmpty()) {
            Text(
                text = text,
                style = GlassType.callout,
                color = glass.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Capsule segmented control; a lighter pill slides to the selected option (none while [selectedIndex] is -1). */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val glass = LocalGlass.current
    val position by animateFloatAsState(selectedIndex.coerceAtLeast(0).toFloat(), tween(240), label = "segment")
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(CircleShape)
            .background(glass.controlFill)
            .padding(3.dp),
    ) {
        val segment = maxWidth / options.size
        if (selectedIndex >= 0) {
            Box(
                Modifier
                    .offset(x = segment * position)
                    .width(segment)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(glass.controlFillSelected),
            )
        }
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { index, option ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable(enabled = enabled, role = Role.Tab) { onSelect(index) }
                        .semantics { selected = index == selectedIndex },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option,
                        style = GlassType.callout,
                        color = if (index == selectedIndex) glass.textPrimary else glass.textSecondary,
                    )
                }
            }
        }
    }
}

/** Flat switch: a translucent track that fills with the accent when on, white capsule thumb. */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val glass = LocalGlass.current
    val progress by animateFloatAsState(if (checked) 1f else 0f, tween(200), label = "switch")
    val track by animateColorAsState(if (checked) glass.accent else glass.track, tween(200), label = "switchTrack")
    Canvas(
        modifier = modifier
            .size(width = 52.dp, height = 32.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
            .semantics {
                contentDescription = label
                stateDescription = if (checked) "On" else "Off"
            },
    ) {
        drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2f))
        val inset = 3.dp.toPx()
        val thumbH = size.height - 2 * inset
        val thumbW = thumbH * 1.25f
        val left = inset + (size.width - 2 * inset - thumbW) * progress
        drawRoundRect(
            Color.White,
            topLeft = Offset(left, inset),
            size = Size(thumbW, thumbH),
            cornerRadius = CornerRadius(thumbH / 2f),
        )
    }
}

/**
 * Flat slider over whole steps of [range]: a translucent track that fills with the accent up to
 * a white thumb. [onChange] follows the finger; [onDone] gets the value it was let go at.
 */
@Composable
fun GlassSlider(
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    onDone: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val glass = LocalGlass.current
    val latestValue by rememberUpdatedState(value)
    val latestChange by rememberUpdatedState(onChange)
    val latestDone by rememberUpdatedState(onDone)
    val steps = (range.last - range.first).coerceAtLeast(1)
    fun valueAt(x: Float, width: Float): Int {
        val fraction = ((x - 12.dp.value) / (width - 24.dp.value)).coerceIn(0f, 1f)
        return range.first + (fraction * steps).roundToInt()
    }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .pointerInput(range, enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    val v = valueAt(offset.x / density, size.width / density)
                    latestChange(v)
                    latestDone(v)
                }
            }
            .pointerInput(range, enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = { latestDone(latestValue) },
                    onDragCancel = { latestDone(latestValue) },
                ) { change, _ ->
                    change.consume()
                    val v = valueAt(change.position.x / density, size.width / density)
                    if (v != latestValue) latestChange(v)
                }
            }
            .semantics {
                contentDescription = label
                stateDescription = "$value"
            },
    ) {
        val inset = 12.dp.toPx()
        val trackHeight = 6.dp.toPx()
        val y = size.height / 2f
        val fraction = (value - range.first).toFloat() / steps
        val thumbX = inset + (size.width - 2 * inset) * fraction
        drawRoundRect(glass.track, topLeft = Offset(inset, y - trackHeight / 2f), size = Size(size.width - 2 * inset, trackHeight), cornerRadius = CornerRadius(trackHeight / 2f))
        drawRoundRect(glass.accent, topLeft = Offset(inset, y - trackHeight / 2f), size = Size(thumbX - inset, trackHeight), cornerRadius = CornerRadius(trackHeight / 2f))
        drawCircle(Color.Black.copy(alpha = 0.18f), radius = 11.5.dp.toPx(), center = Offset(thumbX, y + 1.dp.toPx()))
        drawCircle(Color.White, radius = 11.dp.toPx(), center = Offset(thumbX, y))
    }
}

/** A row with a label on the left and a control on the right, inside a grouped panel. */
@Composable
fun SettingRow(label: String, modifier: Modifier = Modifier, control: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = GlassType.body, color = LocalGlass.current.textPrimary, modifier = Modifier.weight(1f))
        control()
    }
}

/** Single-line text field on a translucent capsule. */
@Composable
fun GlassTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val glass = LocalGlass.current
    val focusManager = LocalFocusManager.current
    val shape = RoundedCornerShape(18.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = GlassType.body.copy(color = glass.textPrimary),
        cursorBrush = SolidColor(glass.accent),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        modifier = modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(glass.controlFill)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (value.text.isEmpty()) {
                    Text(placeholder, style = GlassType.body, color = glass.textTertiary)
                }
                inner()
            }
        },
    )
}
