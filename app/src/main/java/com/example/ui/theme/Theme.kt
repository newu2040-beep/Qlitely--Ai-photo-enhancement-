package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import com.example.core.designsystem.LocalQltyPalette
import com.example.core.designsystem.QltyRadius
import com.example.core.designsystem.QltyThemePreset
import com.example.core.designsystem.buildQltyPalette

private val QltyShapes = Shapes(
    small = QltyRadius.SmallShape,
    medium = QltyRadius.CardShape,
    large = QltyRadius.CardShape,
    extraLarge = QltyRadius.CardShape
)

@Composable
fun QLITEYTheme(
    preset: QltyThemePreset = QltyThemePreset.OBSIDIAN_VIOLET,
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val target = buildQltyPalette(preset, darkTheme)

    val bg by animateColorAsState(target.bg, tween(220), label = "theme_bg")
    val surface by animateColorAsState(target.surface, tween(220), label = "theme_surface")
    val surface2 by animateColorAsState(target.surface2, tween(220), label = "theme_surface2")
    val primary by animateColorAsState(target.primary, tween(220), label = "theme_primary")
    val primaryHighlight by animateColorAsState(target.primaryHighlight, tween(220), label = "theme_primary_hl")
    val text by animateColorAsState(target.text, tween(220), label = "theme_text")
    val textMuted by animateColorAsState(target.textMuted, tween(220), label = "theme_text_muted")
    val border by animateColorAsState(target.border, tween(220), label = "theme_border")

    val animatedPalette = target.copy(
        bg = bg,
        surface = surface,
        surface2 = surface2,
        primary = primary,
        primaryHighlight = primaryHighlight,
        text = text,
        textMuted = textMuted,
        border = border
    )

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = animatedPalette.primary,
            onPrimary = animatedPalette.onPrimary,
            secondary = animatedPalette.surface2,
            onSecondary = animatedPalette.text,
            tertiary = animatedPalette.success,
            onTertiary = animatedPalette.bg,
            background = animatedPalette.bg,
            onBackground = animatedPalette.text,
            surface = animatedPalette.surface,
            onSurface = animatedPalette.text,
            surfaceVariant = animatedPalette.surface2,
            onSurfaceVariant = animatedPalette.textMuted,
            outline = animatedPalette.border,
            error = animatedPalette.danger,
            onError = animatedPalette.onPrimary
        )
    } else {
        lightColorScheme(
            primary = animatedPalette.primary,
            onPrimary = animatedPalette.onPrimary,
            secondary = animatedPalette.surface2,
            onSecondary = animatedPalette.text,
            tertiary = animatedPalette.success,
            onTertiary = animatedPalette.onPrimary,
            background = animatedPalette.bg,
            onBackground = animatedPalette.text,
            surface = animatedPalette.surface,
            onSurface = animatedPalette.text,
            surfaceVariant = animatedPalette.surface2,
            onSurfaceVariant = animatedPalette.textMuted,
            outline = animatedPalette.border,
            error = animatedPalette.danger,
            onError = animatedPalette.onPrimary
        )
    }

    CompositionLocalProvider(LocalQltyPalette provides animatedPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = qltyTypography(),
            shapes = QltyShapes,
            content = content
        )
    }
}
