package com.example.template.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkScheme = darkColorScheme(
    primary = Palette.BrandBlue,
    onPrimary = Palette.LightSurface,
    background = Palette.DarkBackground,
    onBackground = Palette.LightSurface,
    surface = Palette.DarkBackground,
    onSurface = Palette.LightSurface,
    surfaceVariant = Palette.DarkSurface,
    onSurfaceVariant = Palette.DarkTextSecondary,
    surfaceContainer = Palette.DarkSurface,
    surfaceContainerHigh = Palette.DarkSurfaceVariant,
    outline = Palette.HairlineDark,
    outlineVariant = Palette.DarkSurfaceVariant,
    error = Palette.AmberAccent,
    secondaryContainer = Palette.DarkSurface,
    onSecondaryContainer = Palette.LightSurface,
)

private val LightScheme = lightColorScheme(
    primary = Palette.BrandBlue,
    onPrimary = Palette.LightSurface,
    background = Palette.LightBackground,
    onBackground = Palette.DarkBackground,
    surface = Palette.LightSurface,
    onSurface = Palette.DarkBackground,
    surfaceVariant = Palette.BlueTintSoft,
    onSurfaceVariant = Palette.LightTextSecondary,
    surfaceContainer = Palette.LightSurface,
    surfaceContainerHigh = Palette.BlueTintSofter,
    outline = Palette.Hairline,
    error = Palette.AmberAccent,
    secondaryContainer = Palette.BlueTintSoft,
    onSecondaryContainer = Palette.BrandBlue,
)

@Composable
fun TemplateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = TemplateTypography,
        content = content,
    )
}
