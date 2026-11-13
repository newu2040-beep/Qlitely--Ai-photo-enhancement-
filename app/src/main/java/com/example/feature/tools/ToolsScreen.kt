package com.example.feature.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.QltyCard
import com.example.core.designsystem.QltyColors
import com.example.core.designsystem.QltyListItem
import com.example.core.designsystem.QltySpace
import com.example.core.designsystem.QltyTopBar
import com.example.core.designsystem.QltyType
import com.example.core.domain.EnhancementPreset
import com.example.core.image.DeviceCapabilities

@Composable
fun ToolsScreen(
    capabilities: DeviceCapabilities,
    isNetworkOnline: Boolean,
    onLaunchPreset: (EnhancementPreset) -> Unit,
    contentPadding: PaddingValues
) {
    val presets = remember { EnhancementPreset.entries }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(QltyColors.Bg)
    ) {
        QltyTopBar(
            title = "Studio Tools",
            subtitle = "Targeted enhancement workflows"
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 600.dp)
                .align(Alignment.CenterHorizontally)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = QltySpace.S16)
                .padding(bottom = contentPadding.calculateBottomPadding() + QltySpace.S24),
            verticalArrangement = Arrangement.spacedBy(QltySpace.S24)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(QltySpace.S12)) {
                Text(
                    text = "Enhancement Workflows",
                    style = QltyType.Heading,
                    color = QltyColors.Text
                )

                presets.forEach { preset ->
                    val icon = when (preset) {
                        EnhancementPreset.AI_ENHANCE -> Icons.Rounded.FlashOn
                        EnhancementPreset.UPSCALE_4K -> Icons.Rounded.Crop
                        EnhancementPreset.RESTORE_OLD -> Icons.Rounded.AutoFixHigh
                        EnhancementPreset.NATURAL_CLARITY -> Icons.Rounded.AutoAwesome
                        EnhancementPreset.LOW_LIGHT_DENOISE -> Icons.Rounded.NightsStay
                    }
                    QltyCard(
                        onClick = { onLaunchPreset(preset) },
                        contentPadding = PaddingValues(QltySpace.S12)
                    ) {
                        QltyListItem(
                            title = preset.title,
                            subtitle = "${preset.subtitle} • Default: ${preset.defaultMode.label} (${preset.defaultResolution.label})",
                            leadingIcon = icon,
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = "Launch ${preset.title}",
                                    tint = QltyColors.TextMuted
                                )
                            },
                            testTag = "tools_preset_${preset.id}"
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(QltySpace.S12)) {
                Text(
                    text = "Device & Pipeline Readiness",
                    style = QltyType.Heading,
                    color = QltyColors.Text
                )

                QltyCard {
                    QltyListItem(
                        title = "Local On-Device DSP",
                        subtitle = if (capabilities.supportsLocalProcessing) {
                            "Ready • ${capabilities.maxHeapMb} MB heap • Up to ${capabilities.maxSafeOutputLongEdge}px long edge"
                        } else {
                            "Constrained (${capabilities.availableStorageMb} MB free storage)"
                        },
                        leadingIcon = Icons.Rounded.Memory
                    )
                    QltyListItem(
                        title = "Cloud AI (Firebase AI Logic)",
                        subtitle = if (isNetworkOnline) {
                            "Online • Gemini 3.8 Flash & Nano Banana 2.1 ready"
                        } else {
                            "Offline • Local AI remains fully available"
                        },
                        leadingIcon = if (isNetworkOnline) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff
                    )
                }
            }
        }
    }
}
