package com.example.template.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The app palette: a dark-first scheme with a single brand blue. Everything the UI uses
 * comes from here, so a rebrand is a change to these few values.
 */
object Palette {
    val BrandBlue = Color(0xFF426EFE)
    val BrandBlueDark = Color(0xFF507BF2)

    // dark theme
    val DarkBackground = Color(0xFF0F0F0F)
    val DarkSurface = Color(0xFF18181B)
    val DarkSurfaceVariant = Color(0xFF0F1115)
    val DarkTextSecondary = Color(0xFF7D7F85)
    val DarkTextTertiary = Color(0xFF8E9094)

    // light theme
    val LightBackground = Color(0xFFFFFFFF)
    val LightSurface = Color(0xFFFFFFFF)
    val LightTextSecondary = Color(0xFF545557)
    val LightTextTertiary = Color(0xFF7F8287)

    // tints & accents
    val BlueTint = Color(0xFFE7EFFF)
    val UserBubble = Color(0xFFEDF3FF)
    val ChipFill = Color(0xFFF5F8FF)
    val ChipBorder = Color(0xFFD6E2FF)
    val Placeholder = Color(0xFF9AA0A6)
    val RailLine = Color(0xFFE3E6EB)
    val BlueTintSoft = Color(0xFFF1F5FD)
    val BlueTintSofter = Color(0xFFF2F6FF)
    val AmberAccent = Color(0xFFF59E0B)
    val Hairline = Color(0xFFD4D4D4)
    val HairlineDark = Color(0xFF2A2A2E)
    val CitationBg = Color(0x1F426EFE)

    // status
    val Ok = Color(0xFF37C98B)
    val Err = Color(0xFFE05C8A)
}

/**
 * A small accent ramp for demo content (chips, list markers, avatars). Charts, if you add
 * them, want the same treatment: one colour per series, stable across renders.
 */
val AccentPalette = listOf(
    Color(0xFF4C8DFF),
    Color(0xFFFF8A3D),
    Color(0xFF37C98B),
    Color(0xFFE05C8A),
    Color(0xFFB98CFF),
    Color(0xFFD9C02C),
    Color(0xFF39B7C9),
    Color(0xFFF2645A),
)
