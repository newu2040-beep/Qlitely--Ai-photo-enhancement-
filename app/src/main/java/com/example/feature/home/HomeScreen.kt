package com.example.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.core.data.UiState
import com.example.core.designsystem.QltyBottomSheet
import com.example.core.designsystem.QltyButton
import com.example.core.designsystem.QltyButtonStyle
import com.example.core.designsystem.QltyCard
import com.example.core.designsystem.QltyChip
import com.example.core.designsystem.QltyColors
import com.example.core.designsystem.QltyEmptyState
import com.example.core.designsystem.QltyIconButton
import com.example.core.designsystem.QltyListItem
import com.example.core.designsystem.QltyRadius
import com.example.core.designsystem.QltySizes
import com.example.core.designsystem.QltySpace
import com.example.core.designsystem.QltyType
import com.example.core.domain.EnhancementJob
import com.example.core.domain.EnhancementPreset
import com.example.core.domain.LibraryAsset
import java.io.File

@Composable
fun HomeScreen(
    userDisplayName: String,
    userPhotoUrl: String?,
    isDarkMode: Boolean,
    libraryState: UiState<List<LibraryAsset>>,
    jobs: List<EnhancementJob>,
    isImporting: Boolean,
    onToggleDarkMode: () -> Unit,
    onPickPhoto: (EnhancementPreset?) -> Unit,
    onBrowseFiles: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenAsset: (LibraryAsset) -> Unit,
    onOpenProfile: () -> Unit,
    contentPadding: PaddingValues
) {
    var showActivitySheet by remember { mutableStateOf(false) }
    val firstName = userDisplayName.trim().substringBefore(" ").takeIf { it.isNotBlank() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(QltyColors.Bg)
    ) {
        // Subtle editorial ambient glow at top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(QltyColors.AmbientTopGlow)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(bottom = contentPadding.calculateBottomPadding() + QltySpace.S24)
                .padding(horizontal = QltySpace.S16),
            verticalArrangement = Arrangement.spacedBy(QltySpace.S24)
        ) {
            // Header: Brand mark + Dark/Light toggle + Activity bell + User avatar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(QltySizes.TopBarHeight),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = QltyRadius.PillShape,
                    color = QltyColors.Primary,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(QltyColors.PrimaryGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "QLITEY",
                            tint = QltyColors.OnPrimary,
                            modifier = Modifier.size(QltySizes.IconChip)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(QltySpace.S8)
                ) {
                    QltyIconButton(
                        icon = if (isDarkMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                        contentDescription = if (isDarkMode) "Switch to Light Mode" else "Switch to Dark Mode",
                        onClick = onToggleDarkMode,
                        testTag = "home_theme_toggle_button"
                    )

                    QltyIconButton(
                        icon = Icons.Rounded.Notifications,
                        contentDescription = "Recent activity",
                        onClick = { showActivitySheet = true },
                        testTag = "home_activity_button"
                    )

                    Surface(
                        shape = QltyRadius.PillShape,
                        color = QltyColors.Surface2,
                        border = BorderStroke(QltySizes.Hairline, QltyColors.Border),
                        modifier = Modifier
                            .size(QltySizes.TouchTarget)
                            .clip(QltyRadius.PillShape)
                            .clickable(onClick = onOpenProfile)
                            .testTag("home_avatar_button")
                    ) {
                        if (!userPhotoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = userPhotoUrl,
                                contentDescription = "Account profile",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = (firstName?.firstOrNull()?.uppercaseChar() ?: 'Q').toString(),
                                    style = QltyType.BodyMedium,
                                    color = QltyColors.Text
                                )
                            }
                        }
                    }
                }
            }

            // Editorial Greeting + Headline
            Column(
                verticalArrangement = Arrangement.spacedBy(QltySpace.S4)
            ) {
                Text(
                    text = if (firstName != null) "Hello, $firstName," else "Hello there,",
                    style = QltyType.Body,
                    color = QltyColors.TextMuted
                )
                Text(
                    text = "Enhance Your Photos",
                    style = QltyType.Title,
                    color = QltyColors.Text
                )
                Text(
                    text = "Upscale, restore and bring out the best in every detail.",
                    style = QltyType.Body,
                    color = QltyColors.TextMuted
                )
            }

            // Hero Upload Photo Glass Card + Quick Source Pills
            Column(verticalArrangement = Arrangement.spacedBy(QltySpace.S12)) {
                Surface(
                    shape = QltyRadius.CardShape,
                    color = QltyColors.Surface2.copy(alpha = 0.72f),
                    border = BorderStroke(QltySizes.Hairline, QltyColors.PrimaryHighlight.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(QltyRadius.CardShape)
                        .clickable(enabled = !isImporting) { onPickPhoto(null) }
                        .testTag("home_upload_photo_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(QltyColors.CardGlassGradient)
                            .padding(horizontal = QltySpace.S16, vertical = QltySpace.S24),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Spacer(modifier = Modifier.width(QltySizes.TouchTarget))

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(QltySpace.S8)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(QltyRadius.PillShape)
                                    .background(QltyColors.PrimaryGradient),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudUpload,
                                    contentDescription = null,
                                    tint = QltyColors.OnPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Text(
                                text = if (isImporting) "Importing Photo..." else "Upload Photo",
                                style = QltyType.Heading,
                                color = QltyColors.Text,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Tap for Photo Picker or browse files below",
                                style = QltyType.Caption,
                                color = QltyColors.TextMuted,
                                textAlign = TextAlign.Center
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(QltySizes.TouchTarget)
                                .clip(QltyRadius.PillShape)
                                .background(QltyColors.Surface.copy(alpha = 0.75f))
                                .border(QltySizes.Hairline, QltyColors.Border, QltyRadius.PillShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = "Choose photo",
                                tint = QltyColors.Text,
                                modifier = Modifier.size(QltySizes.IconChip)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(QltySpace.S8)
                ) {
                    QltyChip(
                        label = "Gallery Picker",
                        selected = true,
                        leadingIcon = Icons.Rounded.PhotoLibrary,
                        onClick = { onPickPhoto(null) },
                        enabled = !isImporting,
                        modifier = Modifier.weight(1f),
                        testTag = "home_source_gallery_chip"
                    )
                    QltyChip(
                        label = "Browse Files",
                        selected = false,
                        leadingIcon = Icons.Rounded.FolderOpen,
                        onClick = onBrowseFiles,
                        enabled = !isImporting,
                        modifier = Modifier.weight(1f),
                        testTag = "home_source_files_chip"
                    )
                }
            }

            // 3 Quick Action Tiles with Circular Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(QltySpace.S12)
            ) {
                QuickActionTile(
                    icon = Icons.Rounded.FlashOn,
                    label = "AI Enhance",
                    onClick = { onPickPhoto(EnhancementPreset.AI_ENHANCE) },
                    modifier = Modifier.weight(1f),
                    testTag = "quick_ai_enhance"
                )
                QuickActionTile(
                    icon = Icons.Rounded.Crop,
                    label = "Upscale 4K",
                    onClick = { onPickPhoto(EnhancementPreset.UPSCALE_4K) },
                    modifier = Modifier.weight(1f),
                    testTag = "quick_upscale_4k"
                )
                QuickActionTile(
                    icon = Icons.Rounded.AutoFixHigh,
                    label = "Restore Old",
                    onClick = { onPickPhoto(EnhancementPreset.RESTORE_OLD) },
                    modifier = Modifier.weight(1f),
                    testTag = "quick_restore_old"
                )
            }

            // Quick Access / Recent Section
            Column(
                verticalArrangement = Arrangement.spacedBy(QltySpace.S12)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Quick Access",
                        style = QltyType.Heading,
                        color = QltyColors.Text
                    )
                    Row(
                        modifier = Modifier
                            .clip(QltyRadius.PillShape)
                            .clickable(onClick = onOpenLibrary)
                            .padding(horizontal = QltySpace.S12, vertical = QltySpace.S8)
                            .testTag("home_see_all_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(QltySpace.S4)
                    ) {
                        Text(
                            text = "See all",
                            style = QltyType.BodyMedium,
                            color = QltyColors.TextMuted
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = QltyColors.TextMuted,
                            modifier = Modifier.size(QltySizes.IconChip)
                        )
                    }
                }

                when (libraryState) {
                    is UiState.Loading -> {
                        QltyCard {
                            Text(
                                text = "Loading your recent enhancements...",
                                style = QltyType.Body,
                                color = QltyColors.TextMuted
                            )
                        }
                    }
                    is UiState.Error -> {
                        QltyCard {
                            Text(
                                text = libraryState.message,
                                style = QltyType.Body,
                                color = QltyColors.Danger
                            )
                        }
                    }
                    is UiState.Success -> {
                        val latest = libraryState.data.firstOrNull()
                        if (latest == null) {
                            QltyEmptyState(
                                icon = Icons.Rounded.PhotoLibrary,
                                title = "Turn Memories into Masterpieces",
                                description = "Import a photo above to enhance clarity, restore vintage prints, or upscale to 4K.",
                                actionLabel = "Import First Photo",
                                onAction = { onPickPhoto(null) }
                            )
                        } else {
                            QltyCard(
                                onClick = { onOpenAsset(latest) },
                                contentPadding = PaddingValues(QltySpace.S12),
                                modifier = Modifier.testTag("home_recent_asset_card")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(QltySpace.S12)
                                ) {
                                    val thumbFile = File(latest.thumbnailPath.ifBlank { latest.localOutputPath })
                                    Surface(
                                        shape = QltyRadius.SmallShape,
                                        color = QltyColors.Surface2,
                                        modifier = Modifier.size(56.dp)
                                    ) {
                                        if (thumbFile.exists()) {
                                            AsyncImage(
                                                model = thumbFile,
                                                contentDescription = latest.displayName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Rounded.PhotoLibrary,
                                                    contentDescription = null,
                                                    tint = QltyColors.PrimaryHighlight
                                                )
                                            }
                                        }
                                    }
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(QltySpace.S4)
                                    ) {
                                        Text(
                                            text = latest.displayName,
                                            style = QltyType.BodyMedium,
                                            color = QltyColors.Text,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${latest.resolutionLabel} • ${latest.outputWidth} × ${latest.outputHeight}",
                                            style = QltyType.Caption,
                                            color = QltyColors.TextMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                        contentDescription = "Open result",
                                        tint = QltyColors.TextMuted,
                                        modifier = Modifier.size(QltySizes.IconChip)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showActivitySheet) {
        QltyBottomSheet(
            onDismissRequest = { showActivitySheet = false },
            title = "Enhancement Activity"
        ) {
            if (jobs.isEmpty()) {
                Text(
                    text = "No enhancement jobs yet. Pick a photo to start processing.",
                    style = QltyType.Body,
                    color = QltyColors.TextMuted
                )
            } else {
                jobs.take(5).forEach { job ->
                    QltyListItem(
                        title = job.sourceName,
                        subtitle = "${job.mode.uppercase()} • ${job.status} • ${job.progressStage}",
                        leadingIcon = Icons.Rounded.History
                    )
                }
            }
            QltyButton(
                text = "Close",
                onClick = { showActivitySheet = false },
                style = QltyButtonStyle.Secondary,
                testTag = "close_activity_sheet"
            )
        }
    }
}

@Composable
private fun QuickActionTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    Surface(
        shape = QltyRadius.CardShape,
        color = QltyColors.Surface,
        border = BorderStroke(QltySizes.Hairline, QltyColors.Border),
        modifier = modifier
            .height(104.dp)
            .clip(QltyRadius.CardShape)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(QltySpace.S12),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(QltyRadius.PillShape)
                    .background(QltyColors.Surface2)
                    .border(QltySizes.Hairline, QltyColors.Border, QltyRadius.PillShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = QltyColors.PrimaryHighlight,
                    modifier = Modifier.size(QltySizes.IconChip)
                )
            }
            Spacer(modifier = Modifier.height(QltySpace.S8))
            Text(
                text = label,
                style = QltyType.CaptionMedium,
                color = QltyColors.Text,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
