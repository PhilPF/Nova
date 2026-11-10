package com.philpf.nova.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.philpf.nova.R

/**
 * The flat "liquid glass" look in one theme: frosted panels over a colourful backdrop. From the
 * Glass toolkit of PhilPF/dualsense_manager.
 */
@Immutable
data class GlassPalette(
    val isDark: Boolean,
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    /** Faint gradient laid over the blur, top to bottom. */
    val sheenTop: Color,
    val sheenMiddle: Color,
    val sheenBottom: Color,
    /** The thin light rim, from the top-left corner round to the bottom-right. */
    val rim: List<Color>,
    /** Hairline that defines panel edges on a light background. */
    val outline: Color,
    /** Controls on glass: buttons, chips, text fields. */
    val controlFill: Color,
    val controlFillSelected: Color,
    val track: Color,
    val accent: Color,
    val danger: Color,
    /** How strongly the colours of the album art glow behind the glass. */
    val glowAlpha: Float,
    /** Fill over the blur of sheets, so the colours behind don't drown what they show. */
    val frost: Color,
    val scrim: Color,
)

val DarkGlass = GlassPalette(
    isDark = true,
    backgroundTop = Color(0xFF10142A),
    backgroundBottom = Color(0xFF06070D),
    textPrimary = Color(0xF5FFFFFF),
    textSecondary = Color(0xA6FFFFFF),
    textTertiary = Color(0x70FFFFFF),
    sheenTop = Color(0x24FFFFFF),
    sheenMiddle = Color(0x12FFFFFF),
    sheenBottom = Color(0x0AFFFFFF),
    rim = listOf(Color(0x4DFFFFFF), Color(0x14FFFFFF), Color(0x0AFFFFFF), Color(0x2EFFFFFF)),
    outline = Color(0x00000000),
    controlFill = Color(0x1AFFFFFF),
    controlFillSelected = Color(0x38FFFFFF),
    track = Color(0x29FFFFFF),
    accent = Color(0xFF8DB3FF),
    danger = Color(0xFFFF7A7A),
    glowAlpha = 0.62f,
    frost = Color(0x8C0B0E1A),
    scrim = Color(0x73000000),
)

val LightGlass = GlassPalette(
    isDark = false,
    backgroundTop = Color(0xFFF7F8FC),
    backgroundBottom = Color(0xFFE3E6F0),
    textPrimary = Color(0xEB0E1222),
    textSecondary = Color(0x990E1222),
    textTertiary = Color(0x6B0E1222),
    sheenTop = Color(0x99FFFFFF),
    sheenMiddle = Color(0x7AFFFFFF),
    sheenBottom = Color(0x6BFFFFFF),
    rim = listOf(Color(0xF2FFFFFF), Color(0x80FFFFFF), Color(0x4DFFFFFF), Color(0xB3FFFFFF)),
    outline = Color(0x140E1222),
    controlFill = Color(0x0F0E1222),
    controlFillSelected = Color(0xE6FFFFFF),
    track = Color(0x1F0E1222),
    accent = Color(0xFF2F6BFF),
    danger = Color(0xFFD93A3A),
    glowAlpha = 0.5f,
    frost = Color(0x99F7F8FC),
    scrim = Color(0x2E000000),
)

val LocalGlass = staticCompositionLocalOf { DarkGlass }

/** Whether text uses the monospaced font (a setting). */
val LocalMonospace = staticCompositionLocalOf { false }

/** The app's text styles, in the system font or, when chosen, the monospaced one. */
object GlassType {
    val largeTitle: TextStyle @Composable @ReadOnlyComposable get() = styles().largeTitle
    val title: TextStyle @Composable @ReadOnlyComposable get() = styles().title
    val headline: TextStyle @Composable @ReadOnlyComposable get() = styles().headline
    val body: TextStyle @Composable @ReadOnlyComposable get() = styles().body
    val callout: TextStyle @Composable @ReadOnlyComposable get() = styles().callout
    val caption: TextStyle @Composable @ReadOnlyComposable get() = styles().caption
    val section: TextStyle @Composable @ReadOnlyComposable get() = styles().section

    /**
     * The serif for names that stand out: the app's name, the song playing, the names of albums,
     * artists and playlists. Newsreader (SIL Open Font License), close to Tiempos Headline;
     * [Headline] is the family to change for another.
     */
    val display: TextStyle @Composable @ReadOnlyComposable get() = DisplayStyle
}

/** Newsreader at its largest optical size (drawn for headlines), medium weight. */
@OptIn(ExperimentalTextApi::class)
val Headline = FontFamily(
    Font(
        R.font.newsreader,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500), FontVariation.Setting("opsz", 72f)),
    ),
)

private val DisplayStyle = TextStyle(fontFamily = Headline, fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.2).sp)

private class TypeScale(family: FontFamily) {
    val largeTitle = TextStyle(fontFamily = family, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
    val title = TextStyle(fontFamily = family, fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
    val headline = TextStyle(fontFamily = family, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
    val body = TextStyle(fontFamily = family, fontSize = 15.sp, lineHeight = 21.sp)
    val callout = TextStyle(fontFamily = family, fontSize = 14.sp, lineHeight = 19.sp)
    val caption = TextStyle(fontFamily = family, fontSize = 13.sp, lineHeight = 17.sp)
    val section = TextStyle(fontFamily = family, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
}

private val SystemType = TypeScale(FontFamily.Default)
private val MonospaceType = TypeScale(FontFamily.Monospace)

@Composable
@ReadOnlyComposable
private fun styles(): TypeScale = if (LocalMonospace.current) MonospaceType else SystemType

/**
 * A colour (from album art) made readable as a UI colour on this palette: very dark colours are lifted
 * toward white on the dark theme, very light ones (white, yellow) pushed toward ink on the light one.
 */
fun Color.onGlass(palette: GlassPalette): Color {
    val l = luminance()
    return if (palette.isDark) {
        if (l >= 0.22f) this else lerp(this, Color.White, ((0.22f - l) / (1f - l)).coerceIn(0f, 0.75f))
    } else {
        if (l <= 0.55f) this else lerp(this, Color(0xFF0E1222), ((l - 0.55f) / l).coerceIn(0f, 0.6f))
    }
}

/** Text colour that reads on a solid fill of [background]. */
fun contentColorOn(background: Color): Color =
    if (background.luminance() > 0.45f) Color(0xFF101219) else Color.White

@Composable
fun NovaTheme(dark: Boolean, monospace: Boolean = false, content: @Composable () -> Unit) {
    val glass = if (dark) DarkGlass else LightGlass
    val scheme = if (dark) {
        darkColorScheme(
            primary = glass.accent,
            background = glass.backgroundBottom,
            surface = glass.backgroundBottom,
            onBackground = glass.textPrimary,
            onSurface = glass.textPrimary,
        )
    } else {
        lightColorScheme(
            primary = glass.accent,
            background = glass.backgroundBottom,
            surface = glass.backgroundBottom,
            onBackground = glass.textPrimary,
            onSurface = glass.textPrimary,
        )
    }
    MaterialTheme(colorScheme = scheme) {
        // The font first, so the default text style below is read in it.
        CompositionLocalProvider(LocalMonospace provides monospace) {
            CompositionLocalProvider(
                LocalGlass provides glass,
                LocalContentColor provides glass.textPrimary,
                LocalTextStyle provides GlassType.body,
                content = content,
            )
        }
    }
}
