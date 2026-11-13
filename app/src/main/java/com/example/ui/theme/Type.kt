package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.example.core.designsystem.QltyType

@Composable
@ReadOnlyComposable
fun qltyTypography(): Typography = Typography(
    headlineMedium = QltyType.Title,
    titleLarge = QltyType.Title,
    titleMedium = QltyType.Heading,
    titleSmall = QltyType.BodyMedium,
    bodyLarge = QltyType.Body,
    bodyMedium = QltyType.Body,
    bodySmall = QltyType.Caption,
    labelLarge = QltyType.BodyMedium,
    labelMedium = QltyType.CaptionMedium,
    labelSmall = QltyType.Caption
)
