package com.techmare.pricewatch.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.techmare.pricewatch.R

/**
 * Market-terminal palette. Every colour has one meaning:
 * yellow is you, red is "you're more expensive", green is "you're cheaper", grey is "about the same".
 */
@Immutable
data class AppColors(
    /** Screen background: night blue, not black. */
    val paper: Color,
    /** Raised surfaces: the board, rows on press, the bottom bar. */
    val panel: Color,
    val panelHigh: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    val rule: Color,
    /** You: your price marker, the selected tab. */
    val signal: Color,
    /** You're cheaper / prices going your way. */
    val ok: Color,
    /** Warnings that aren't about price: a shop that couldn't be read, out of stock. */
    val mid: Color,
    /** You're more expensive. */
    val high: Color,
) {
    // older names kept so every screen reads the same tokens
    val panelText get() = ink
    val panelMuted get() = muted
    val panelTick get() = rule
}

private val Terminal = AppColors(
    paper = Color(0xFF0F1318),
    panel = Color(0xFF171C22),
    panelHigh = Color(0xFF1E242B),
    ink = Color(0xFFE7EBEF),
    muted = Color(0xFF8A94A0),
    faint = Color(0xFF5A636E),
    rule = Color(0xFF252C35),
    signal = Color(0xFFFFC21A),
    ok = Color(0xFF34D27B),
    mid = Color(0xFFE9A23B),
    high = Color(0xFFFF5C4D),
)

val LocalAppColors = staticCompositionLocalOf { Terminal }

object Fonts {
    val condensed = FontFamily(
        Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
        Font(R.font.barlow_condensed_bold, FontWeight.Bold),
    )
    val text = FontFamily(
        Font(R.font.barlow_semicondensed_regular, FontWeight.Normal),
        Font(R.font.barlow_semicondensed_medium, FontWeight.Medium),
    )
    /** Every price and difference: figures line up in columns like a quote board. */
    val numbers = FontFamily(
        Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
        Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
    )
}

/** Number styles, sized for where they sit. */
object Num {
    val hero = TextStyle(fontFamily = Fonts.numbers, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-1).sp)
    val board = TextStyle(fontFamily = Fonts.numbers, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.5).sp)
    val row = TextStyle(fontFamily = Fonts.numbers, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp)
    val rowStrong = TextStyle(fontFamily = Fonts.numbers, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp)
    val small = TextStyle(fontFamily = Fonts.numbers, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp)
}

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontFamily = Fonts.condensed, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = Fonts.condensed, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Fonts.condensed, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 23.sp),
    titleSmall = TextStyle(fontFamily = Fonts.condensed, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontFamily = Fonts.text, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Fonts.text, fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Fonts.text, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Fonts.text, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Fonts.text, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp),
)

/** Always dark: a quote board reads best on a dark screen, day or night. */
@Composable
fun PriceTheme(content: @Composable () -> Unit) {
    val c = Terminal
    val scheme = darkColorScheme(
        primary = c.signal, onPrimary = Color(0xFF15181C),
        background = c.paper, surface = c.paper, onBackground = c.ink, onSurface = c.ink,
        surfaceVariant = c.panel, onSurfaceVariant = c.muted, outline = c.faint, outlineVariant = c.rule,
        surfaceContainer = c.panel, surfaceContainerHigh = c.panelHigh, surfaceContainerHighest = c.panelHigh,
        error = c.high,
    )
    CompositionLocalProvider(LocalAppColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}
