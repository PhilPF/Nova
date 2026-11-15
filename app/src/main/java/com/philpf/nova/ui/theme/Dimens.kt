package com.philpf.nova.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's spacing, on a 4 dp grid. One rule ties it together: panels, cards and controls
 * stand [margin] from the screen's edge, and the text and pictures in them, and in rows, start
 * [margin] + [inset] from it (24 dp), on every screen and in every sheet.
 */
object Space {
    /** From the screen's (or a sheet's) edge to panels, cards and controls. */
    val margin = 16.dp

    /** A row's content inside its highlight, and a panel's padding around its rows. */
    val inset = 8.dp

    /** Inside a panel or a card, around what is not a row. */
    val padding = 16.dp

    /** Between neighbours: cards, buttons, fields, a picture and its text. */
    val gap = 12.dp

    /** Between a label and what it labels, and small separations. */
    val small = 8.dp

    /** Between sections of a page or a sheet. */
    val section = 24.dp
}

/** The app's corner radii: three sizes of surface, and small pictures. */
object Radius {
    /** Sheets, the player's picture, panels. */
    val panel = 28.dp

    /** Cards, text areas, row highlights in panels. */
    val card = 20.dp

    /** Row highlights. */
    val row = 16.dp

    /** Small pictures in rows (48 dp and under). */
    val thumb = 12.dp
}

/**
 * Lets a row reach [by] past each side of what holds it, so that its highlight lines up with
 * panels and its content with text: rows in a column padded by [Space.margin] + [Space.inset]
 * span from [Space.margin].
 */
fun Modifier.bleed(by: Dp = Space.inset): Modifier = layout { measurable, constraints ->
    val extra = by.roundToPx() * 2
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = (constraints.minWidth + extra).coerceAtLeast(0),
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth,
        ),
    )
    layout(placeable.width - extra, placeable.height) { placeable.place(-by.roundToPx(), 0) }
}
