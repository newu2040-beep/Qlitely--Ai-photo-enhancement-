package com.example.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

enum class QltyThemePreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val darkPrimary: Color,
    val darkPrimaryHighlight: Color,
    val lightPrimary: Color,
    val lightPrimaryHighlight: Color
) {
    OBSIDIAN_VIOLET(
        id = "obsidian_violet",
        title = "Obsidian Violet",
        subtitle = "Editorial studio plum",
        darkPrimary = Color(0xFF8E3DFF),
        darkPrimaryHighlight = Color(0xFFC084FC),
        lightPrimary = Color(0xFF7224E5),
        lightPrimaryHighlight = Color(0xFF8E3DFF)
    ),
    AURORA_EMERALD(
        id = "aurora_emerald",
        title = "Aurora Emerald",
        subtitle = "Crisp jade glow",
        darkPrimary = Color(0xFF10B981),
        darkPrimaryHighlight = Color(0xFF6EE7B7),
        lightPrimary = Color(0xFF047857),
        lightPrimaryHighlight = Color(0xFF10B981)
    ),
    CRIMSON_SUNSET(
        id = "crimson_sunset",
        title = "Crimson Sunset",
        subtitle = "Warm coral rose",
        darkPrimary = Color(0xFFF43F5E),
        darkPrimaryHighlight = Color(0xFFFDA4AF),
        lightPrimary = Color(0xFFBE123C),
        lightPrimaryHighlight = Color(0xFFE11D48)
    ),
    COBALT_STUDIO(
        id = "cobalt_studio",
        title = "Cobalt Studio",
        subtitle = "Clean sapphire blue",
        darkPrimary = Color(0xFF3B82F6),
        darkPrimaryHighlight = Color(0xFF93C5FD),
        lightPrimary = Color(0xFF1D4ED8),
        lightPrimaryHighlight = Color(0xFF2563EB)
    ),
    AMBER_GOLD(
        id = "amber_gold",
        title = "Amber Gold",
        subtitle = "Warm cinema gold",
        darkPrimary = Color(0xFFF59E0B),
        darkPrimaryHighlight = Color(0xFFFCD34D),
        lightPrimary = Color(0xFFB45309),
        lightPrimaryHighlight = Color(0xFFD97706)
    );

    companion object {
        fun fromId(id: String?): QltyThemePreset =
            entries.firstOrNull { it.id == id } ?: OBSIDIAN_VIOLET
    }
}

@Immutable
data class QltyPalette(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val primary: Color,
    val primaryHighlight: Color,
    val onPrimary: Color,
    val text: Color,
    val textMuted: Color,
    val border: Color,
    val danger: Color,
    val success: Color,
    val overlayDark: Color,
    val onOverlay: Color = Color(0xFFFFFFFF)
) {
    val primaryGradient: Brush
        get() = Brush.horizontalGradient(
            colors = listOf(primary, primaryHighlight)
        )

    val cardGlassGradient: Brush
        get() = Brush.verticalGradient(
            colors = if (isDark) {
                listOf(surface2.copy(alpha = 0.9f), surface.copy(alpha = 0.85f))
            } else {
                listOf(surface, surface2.copy(alpha = 0.72f))
            }
        )

    val ambientTopGlow: Brush
        get() = Brush.radialGradient(
            colors = listOf(primary.copy(alpha = if (isDark) 0.34f else 0.16f), bg.copy(alpha = 0f))
        )
}

fun buildQltyPalette(preset: QltyThemePreset, isDark: Boolean): QltyPalette {
    return if (isDark) {
        when (preset) {
            QltyThemePreset.OBSIDIAN_VIOLET -> QltyPalette(
                isDark = true,
                bg = Color(0xFF0E0816),
                surface = Color(0xFF181024),
                surface2 = Color(0xFF251938),
                primary = preset.darkPrimary,
                primaryHighlight = preset.darkPrimaryHighlight,
                onPrimary = Color(0xFFFFFFFF),
                text = Color(0xFFF7F3FF),
                textMuted = Color(0xFFB2A4C9),
                border = Color(0xFF3D2C57),
                danger = Color(0xFFFF5C7A),
                success = Color(0xFF2BD98F),
                overlayDark = Color(0xCC0E0816)
            )
            QltyThemePreset.AURORA_EMERALD -> QltyPalette(
                isDark = true,
                bg = Color(0xFF071310),
                surface = Color(0xFF0F201B),
                surface2 = Color(0xFF173129),
                primary = preset.darkPrimary,
                primaryHighlight = preset.darkPrimaryHighlight,
                onPrimary = Color(0xFFFFFFFF),
                text = Color(0xFFF1FDF8),
                textMuted = Color(0xFF9AC2B4),
                border = Color(0xFF264D40),
                danger = Color(0xFFFF5C7A),
                success = Color(0xFF34D399),
                overlayDark = Color(0xCC071310)
            )
            QltyThemePreset.CRIMSON_SUNSET -> QltyPalette(
                isDark = true,
                bg = Color(0xFF16090D),
                surface = Color(0xFF241118),
                surface2 = Color(0xFF351923),
                primary = preset.darkPrimary,
                primaryHighlight = preset.darkPrimaryHighlight,
                onPrimary = Color(0xFFFFFFFF),
                text = Color(0xFFFFF3F5),
                textMuted = Color(0xFFCFA3AE),
                border = Color(0xFF522A37),
                danger = Color(0xFFFF6B81),
                success = Color(0xFF2BD98F),
                overlayDark = Color(0xCC16090D)
            )
            QltyThemePreset.COBALT_STUDIO -> QltyPalette(
                isDark = true,
                bg = Color(0xFF080E1A),
                surface = Color(0xFF101A2C),
                surface2 = Color(0xFF192842),
                primary = preset.darkPrimary,
                primaryHighlight = preset.darkPrimaryHighlight,
                onPrimary = Color(0xFFFFFFFF),
                text = Color(0xFFF2F7FF),
                textMuted = Color(0xFFA0B3D1),
                border = Color(0xFF2A3E63),
                danger = Color(0xFFFF5C7A),
                success = Color(0xFF2BD98F),
                overlayDark = Color(0xCC080E1A)
            )
            QltyThemePreset.AMBER_GOLD -> QltyPalette(
                isDark = true,
                bg = Color(0xFF141008),
                surface = Color(0xFF211A0E),
                surface2 = Color(0xFF302616),
                primary = preset.darkPrimary,
                primaryHighlight = preset.darkPrimaryHighlight,
                onPrimary = Color(0xFF141008),
                text = Color(0xFFFFF9EE),
                textMuted = Color(0xFFCBBBA0),
                border = Color(0xFF4A3B23),
                danger = Color(0xFFFF5C7A),
                success = Color(0xFF2BD98F),
                overlayDark = Color(0xCC141008)
            )
        }
    } else {
        // High-contrast clean Light mode variants (>= 4.5:1 body text ratio)
        when (preset) {
            QltyThemePreset.OBSIDIAN_VIOLET -> QltyPalette(
                isDark = false,
                bg = Color(0xFFF7F4FC),
                surface = Color(0xFFFFFFFF),
                surface2 = Color(0xFFEEE8FA),
                primary = preset.lightPrimary,
                primaryHighlight = preset.lightPrimaryHighlight,
                onPrimary = Color(0xFFFFFFFF),
                text = Color(0xFF170F26),
                textMuted = Color(0xFF564A6E),
                border = Color(0xFFD7CCE8),
                danger = Color(0xFFC81E3F),
                success = Color(0xFF0F8A56),
                overlayDark = Color(0xCC170F26)
            )
            QltyThemePreset.AURORA_EMERALD -> QltyPalette(
                isDark = false,
                bg = Color(0xFFF2FAF6),
                surface = Color(0xFFFFFFFF),
                surface2 = Color(0xFFE1F4EB),
                primary = preset.lightPrimary,
                primaryHighlight = preset.lightPrimaryHighlight,
                onPrimary = Color(0xFFFFFFFF),
                text = Color(0xFF0D231B),
                textMuted = Color(0xFF406154),
                border = Color(0xFFC2E2D4),
                danger = Color(0xFFC81E3F),
                success = Color(0xFF047857),
                overlayDark = Color(0xCC0D231B)
            )
            QltyThemePreset.CRIMSON_SUNSET -> QltyPalette(
                isDark = false,
                bg = Color(0xFFFDF3F5),
                surface = Color(0xFFFFFFFF),
                surface2 = Color(0xFFFAE3E8),
                primary = preset.lightPrimary,
                primaryHighlight = preset.lightPrimaryHighlight,
                onPrimary = Color(0xFFFFFFFF),
                text = Color(0xFF280D15),
                textMuted = Color(0xFF6B424D),
                border = Color(0xFFEBC6D0),
                danger = Color(0xFFB91C1C),
                success = Color(0xFF0F8A56),
                overlayDark = Color(0xCC280D15)
            )
            QltyThemePreset.COBALT_STUDIO -> QltyPalette(
                isDark = false,
                bg = Color(0xFFF2F6FD),
                surface = Color(0xFFFFFFFF),
                surface2 = Color(0xFFE3ECFA),
                primary = preset.lightPrimary,
                primaryHighlight = preset.lightPrimaryHighlight,
                onPrimary = Color(0xFFFFFFFF),
                text = Color(0xFF0F192E),
                textMuted = Color(0xFF475A7B),
                border = Color(0xFFC5D5EE),
                danger = Color(0xFFC81E3F),
                success = Color(0xFF0F8A56),
                overlayDark = Color(0xCC0F192E)
            )
            QltyThemePreset.AMBER_GOLD -> QltyPalette(
                isDark = false,
                bg = Color(0xFFFCF8F0),
                surface = Color(0xFFFFFFFF),
                surface2 = Color(0xFFF7ECD9),
                primary = preset.lightPrimary,
                primaryHighlight = preset.lightPrimaryHighlight,
                onPrimary = Color(0xFFFFFFFF),
                text = Color(0xFF231A0B),
                textMuted = Color(0xFF66563D),
                border = Color(0xFFE4D3B5),
                danger = Color(0xFFC81E3F),
                success = Color(0xFF0F8A56),
                overlayDark = Color(0xCC231A0B)
            )
        }
    }
}

val DefaultDarkPalette = buildQltyPalette(QltyThemePreset.OBSIDIAN_VIOLET, isDark = true)
val LocalQltyPalette = staticCompositionLocalOf { DefaultDarkPalette }

object QltyColors {
    val Bg: Color @Composable @ReadOnlyComposable get() = LocalQltyPalette.current.bg
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalQltyPalette.current.surface
    val Surface2: Color @Composable @ReadOnlyComposable get() = LocalQltyPalette.current.surface2
    val Primary: Color @Composable @ReadOnlyComposable get() = LocalQltyPalette.current.primary
    val PrimaryHighlight: Color @Composable @ReadOnlyComposable get() = LocalQltyPalette.current.primaryHighlight
    val OnPrimary: Color @Composable @ReadOnlyComposable get() = LocalQltyPalette.current.onPrimary
    val Text: Color @Composable @ReadOnlyComposable get() = LocalQltyPalette.current.text
    val TextMuted: Color @Composable @ReadOnlyComposable get() = LocalQltyPalette.current.textMuted
    val Border: Color @Composable @ReadOnlyComposable get() = LocalQltyPalette.current.border
    val Danger: Color @Composable @ReadOnlyComposable get() = LocalQltyPalette.current.danger
    val Success: Color @Composable @ReadOnlyComposable get() = LocalQltyPalette.current.success
    val OverlayDark: Color @Composable @ReadOnlyComposable get() = LocalQltyPalette.current.overlayDark
    val OnOverlay: Color @Composable @ReadOnlyComposable get() = LocalQltyPalette.current.onOverlay
    val IsDark: Boolean @Composable @ReadOnlyComposable get() = LocalQltyPalette.current.isDark

    val PrimaryGradient: Brush @Composable @ReadOnlyComposable get() = LocalQltyPalette.current.primaryGradient
    val CardGlassGradient: Brush @Composable @ReadOnlyComposable get() = LocalQltyPalette.current.cardGlassGradient
    val AmbientTopGlow: Brush @Composable @ReadOnlyComposable get() = LocalQltyPalette.current.ambientTopGlow
}

object QltySpace {
    val S4 = 4.dp
    val S8 = 8.dp
    val S12 = 12.dp
    val S16 = 16.dp
    val S24 = 24.dp
    val S32 = 32.dp
}

object QltyRadius {
    val Small = 8.dp
    val Card = 16.dp
    val Pill = 999.dp

    val SmallShape = RoundedCornerShape(Small)
    val CardShape = RoundedCornerShape(Card)
    val PillShape = RoundedCornerShape(Pill)
}

object QltySizes {
    val ButtonHeight = 48.dp
    val InputHeight = 48.dp
    val TouchTarget = 48.dp
    val TopBarHeight = 56.dp
    val BottomNavHeight = 56.dp
    val IconDefault = 24.dp
    val IconChip = 20.dp
    val Hairline = 1.dp
}

val PlusJakartaSansFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

object QltyType {
    val Title: TextStyle
        @Composable @ReadOnlyComposable get() = TextStyle(
            fontFamily = PlusJakartaSansFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            color = QltyColors.Text
        )
    val Heading: TextStyle
        @Composable @ReadOnlyComposable get() = TextStyle(
            fontFamily = PlusJakartaSansFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            color = QltyColors.Text
        )
    val Body: TextStyle
        @Composable @ReadOnlyComposable get() = TextStyle(
            fontFamily = PlusJakartaSansFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = QltyColors.Text
        )
    val BodyMedium: TextStyle
        @Composable @ReadOnlyComposable get() = TextStyle(
            fontFamily = PlusJakartaSansFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = QltyColors.Text
        )
    val InputText: TextStyle
        @Composable @ReadOnlyComposable get() = TextStyle(
            fontFamily = PlusJakartaSansFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            color = QltyColors.Text
        )
    val Caption: TextStyle
        @Composable @ReadOnlyComposable get() = TextStyle(
            fontFamily = PlusJakartaSansFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = QltyColors.TextMuted
        )
    val CaptionMedium: TextStyle
        @Composable @ReadOnlyComposable get() = TextStyle(
            fontFamily = PlusJakartaSansFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = QltyColors.Text
        )
}
