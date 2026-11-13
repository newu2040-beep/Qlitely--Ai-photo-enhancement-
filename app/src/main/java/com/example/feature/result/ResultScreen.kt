package com.example.feature.result

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.core.data.ResultUiState
import com.example.core.designsystem.QltyBottomSheet
import com.example.core.designsystem.QltyButton
import com.example.core.designsystem.QltyButtonStyle
import com.example.core.designsystem.QltyCard
import com.example.core.designsystem.QltyChip
import com.example.core.designsystem.QltyColors
import com.example.core.designsystem.QltyEmptyState
import com.example.core.designsystem.QltyIconButton
import com.example.core.designsystem.QltyInput
import com.example.core.designsystem.QltyListItem
import com.example.core.designsystem.QltyRadius
import com.example.core.designsystem.QltySizes
import com.example.core.designsystem.QltySpace
import com.example.core.designsystem.QltyTopBar
import com.example.core.designsystem.QltyType
import com.example.core.domain.EnhancementAdjustments
import com.example.core.domain.EnhancementPreset
import com.example.core.domain.ExportFormat
import com.example.core.domain.LibraryAsset
import com.example.core.domain.ProcessingMode
import com.example.core.image.ImageImportManager
import com.example.core.image.MediaExportManager
import com.example.feature.editor.BeforeAfterViewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ResultScreen(
    state: ResultUiState,
    autoSaveEnabled: Boolean,
    onBack: () -> Unit,
    onSaveToGallery: () -> Unit,
    onSaveCopyToFiles: (suggestedFilename: String) -> Unit,
    onToggleAutoSave: (Boolean) -> Unit,
    onReEnhance: (LibraryAsset) -> Unit,
    onRenameAsset: (LibraryAsset, String) -> Unit,
    onDeleteAsset: (LibraryAsset) -> Unit
) {
    val context = LocalContext.current
    var showDetailsSheet by remember { mutableStateOf(false) }
    var compareMode by remember { mutableStateOf(true) }
    var origBmp by remember { mutableStateOf<Bitmap?>(null) }
    var outBmp by remember { mutableStateOf<Bitmap?>(null) }

    var renameText by remember(state.asset?.displayName) {
        mutableStateOf(state.asset?.displayName ?: "")
    }

    val asset = state.asset

    LaunchedEffect(asset?.localOriginalPath, asset?.localOutputPath) {
        if (asset == null) return@LaunchedEffect
        val decodedOrig = withContext(Dispatchers.IO) {
            if (File(asset.localOriginalPath).exists()) {
                ImageImportManager.decodeOrientedBitmap(asset.localOriginalPath, maxDimension = 1440)
            } else {
                null
            }
        }
        val decodedOut = withContext(Dispatchers.IO) {
            if (File(asset.localOutputPath).exists()) {
                ImageImportManager.decodeOrientedBitmap(asset.localOutputPath, maxDimension = 1440)
            } else {
                null
            }
        }
        origBmp?.recycle()
        outBmp?.recycle()
        origBmp = decodedOrig
        outBmp = decodedOut
        compareMode = decodedOrig != null && decodedOut != null
    }

    DisposableEffect(Unit) {
        onDispose {
            origBmp?.recycle()
            outBmp?.recycle()
        }
    }

    fun triggerShare() {
        val target = asset ?: return
        val intent = MediaExportManager.createShareIntent(
            context = context,
            localOutputPath = target.localOutputPath,
            format = ExportFormat.fromWire(target.format),
            title = target.displayName
        )
        if (intent != null) {
            context.startActivity(Intent.createChooser(intent, "Share Enhanced Photo"))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(QltyColors.Bg)
    ) {
        QltyTopBar(
            title = "Export Result",
            navigationIcon = {
                QltyIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack,
                    testTag = "result_back_button"
                )
            },
            actions = {
                QltyIconButton(
                    icon = Icons.Rounded.Share,
                    contentDescription = "Share enhanced photo",
                    onClick = { triggerShare() },
                    enabled = asset != null,
                    testTag = "result_top_share_button"
                )
                QltyIconButton(
                    icon = Icons.Rounded.Download,
                    contentDescription = "Save to gallery",
                    onClick = onSaveToGallery,
                    enabled = asset != null && !state.isSavingToGallery,
                    testTag = "result_top_save_button"
                )
            }
        )

        if (asset == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(QltySpace.S16),
                contentAlignment = Alignment.Center
            ) {
                QltyEmptyState(
                    icon = Icons.Rounded.AutoAwesome,
                    title = "No Result Loaded",
                    description = "Enhance a photo or select one from My Library to export.",
                    actionLabel = "Go Back",
                    onAction = onBack
                )
            }
            return@Column
        }

        val outputFile = File(asset.localOutputPath)
        val fileExists = outputFile.exists()
        val canCompare = origBmp != null && outBmp != null

        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 600.dp)
                .align(Alignment.CenterHorizontally)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = QltySpace.S16, vertical = QltySpace.S12),
            verticalArrangement = Arrangement.spacedBy(QltySpace.S16)
        ) {
            if (canCompare) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(QltySpace.S8)
                ) {
                    QltyChip(
                        label = "Compare Before / After",
                        selected = compareMode,
                        leadingIcon = Icons.AutoMirrored.Rounded.CompareArrows,
                        onClick = { compareMode = true },
                        modifier = Modifier.weight(1f),
                        testTag = "result_mode_compare"
                    )
                    QltyChip(
                        label = "Final Output",
                        selected = !compareMode,
                        leadingIcon = Icons.Rounded.Photo,
                        onClick = { compareMode = false },
                        modifier = Modifier.weight(1f),
                        testTag = "result_mode_final"
                    )
                }
            }

            if (compareMode && canCompare) {
                BeforeAfterViewer(
                    originalBitmap = origBmp,
                    processedBitmap = outBmp,
                    adjustments = EnhancementAdjustments(),
                    isProcessing = false,
                    processingStage = "",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                )
            } else {
                // Large Final Image Preview Card with truthful output dimension pill
                Surface(
                    shape = QltyRadius.CardShape,
                    color = QltyColors.Surface,
                    border = BorderStroke(QltySizes.Hairline, QltyColors.Border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (fileExists) {
                            AsyncImage(
                                model = outputFile,
                                contentDescription = asset.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Temporary file expired. Re-enhance from original if retained.",
                                    style = QltyType.Body,
                                    color = QltyColors.TextMuted,
                                    modifier = Modifier.padding(QltySpace.S16)
                                )
                            }
                        }

                        Surface(
                            shape = QltyRadius.PillShape,
                            color = QltyColors.OverlayDark,
                            border = BorderStroke(QltySizes.Hairline, QltyColors.PrimaryHighlight.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(QltySpace.S12)
                        ) {
                            Text(
                                text = "${asset.resolutionLabel} • ${asset.outputWidth} × ${asset.outputHeight}",
                                style = QltyType.CaptionMedium,
                                color = QltyColors.OnOverlay,
                                modifier = Modifier.padding(horizontal = QltySpace.S12, vertical = QltySpace.S4)
                            )
                        }
                    }
                }
            }

            // Success Summary Card with truthful measured specs & duration
            val timingSuffix = state.lastProcessingTimeMs?.let {
                " • ${String.format("%.1fs", it / 1000f)}"
            }.orEmpty()
            QltyCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(QltySpace.S12)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = QltyColors.Success,
                        modifier = Modifier.size(28.dp)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(QltySpace.S4)
                    ) {
                        Text(
                            text = "Enhancement Complete!",
                            style = QltyType.Heading,
                            color = QltyColors.Text
                        )
                        Text(
                            text = "${if (asset.isSavedToGallery) "Saved to Gallery" else "Ready to save"} • ${asset.outputWidth}×${asset.outputHeight} ${asset.format} • ${MediaExportManager.formatByteSize(asset.byteSize)}$timingSuffix",
                            style = QltyType.Caption,
                            color = QltyColors.TextMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (!state.errorMessage.isNullOrBlank()) {
                Text(
                    text = state.errorMessage,
                    style = QltyType.Caption,
                    color = QltyColors.Danger
                )
            }

            // Primary Action: Save to Gallery
            QltyButton(
                text = if (asset.isSavedToGallery) "Save Copy to Gallery" else "Save to Gallery",
                onClick = onSaveToGallery,
                leadingIcon = Icons.Rounded.Download,
                isLoading = state.isSavingToGallery,
                enabled = fileExists,
                style = QltyButtonStyle.Primary,
                testTag = "save_to_gallery_button"
            )

            // Secondary Actions Row: Share + Save to Files (SAF)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(QltySpace.S8)
            ) {
                QltyButton(
                    text = "Share",
                    onClick = { triggerShare() },
                    leadingIcon = Icons.Rounded.Share,
                    enabled = fileExists,
                    style = QltyButtonStyle.Secondary,
                    modifier = Modifier.weight(1f),
                    testTag = "share_result_button"
                )
                QltyButton(
                    text = "Save to Files",
                    onClick = {
                        val fmt = ExportFormat.fromWire(asset.format)
                        val clean = asset.displayName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "QLITEY" }
                        onSaveCopyToFiles("${clean}_enhanced.${fmt.extension}")
                    },
                    leadingIcon = Icons.Rounded.FolderOpen,
                    isLoading = state.isSavingCopy,
                    enabled = fileExists,
                    style = QltyButtonStyle.Secondary,
                    modifier = Modifier.weight(1f),
                    testTag = "save_to_files_button"
                )
            }

            // View Details Link
            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(QltyRadius.PillShape)
                    .clickable { showDetailsSheet = true }
                    .padding(horizontal = QltySpace.S16, vertical = QltySpace.S12)
                    .testTag("view_details_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(QltySpace.S4)
            ) {
                Text(
                    text = "View Details",
                    style = QltyType.BodyMedium,
                    color = QltyColors.Text
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = QltyColors.Text,
                    modifier = Modifier.size(QltySizes.IconChip)
                )
            }

            // Auto Save Toggle Card
            QltyCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(QltySpace.S12)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lightbulb,
                        contentDescription = null,
                        tint = QltyColors.PrimaryHighlight,
                        modifier = Modifier.size(QltySizes.IconDefault)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(QltySpace.S4)
                    ) {
                        Text(
                            text = "Auto Save",
                            style = QltyType.BodyMedium,
                            color = QltyColors.Text
                        )
                        Text(
                            text = if (autoSaveEnabled) {
                                "Completed images export automatically to Pictures/QLITEY."
                            } else {
                                "Enable Auto Save for instant gallery exports."
                            },
                            style = QltyType.Caption,
                            color = QltyColors.TextMuted
                        )
                    }
                    Switch(
                        checked = autoSaveEnabled,
                        onCheckedChange = onToggleAutoSave,
                        colors = SwitchDefaults.colors(checkedTrackColor = QltyColors.Primary),
                        modifier = Modifier.testTag("result_autosave_switch")
                    )
                }
            }
        }
    }

    if (showDetailsSheet && asset != null) {
        QltyBottomSheet(
            onDismissRequest = { showDetailsSheet = false },
            title = "Export & File Details"
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(QltySpace.S12)
            ) {
                QltyInput(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = "Asset Title",
                    placeholder = "Enter photo name",
                    trailingContent = {
                        Text(
                            text = "Save",
                            style = QltyType.CaptionMedium,
                            color = QltyColors.PrimaryHighlight,
                            modifier = Modifier
                                .clip(QltyRadius.SmallShape)
                                .clickable {
                                    onRenameAsset(asset, renameText)
                                }
                                .padding(QltySpace.S8)
                                .testTag("rename_save_button")
                        )
                    },
                    testTag = "rename_asset_input"
                )

                QltyListItem(
                    title = "Input -> Output",
                    subtitle = "${asset.inputWidth} × ${asset.inputHeight}  ->  ${asset.outputWidth} × ${asset.outputHeight}"
                )
                QltyListItem(
                    title = "Processing Pipeline",
                    subtitle = "${ProcessingMode.fromWire(asset.mode).label} • ${EnhancementPreset.fromId(asset.presetUsed).title}"
                )
                QltyListItem(
                    title = "Compression & Size",
                    subtitle = "${asset.format} • ${MediaExportManager.formatByteSize(asset.byteSize)} • GPS EXIF stripped"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(QltySpace.S12)
                ) {
                    QltyButton(
                        text = "Re-enhance",
                        onClick = {
                            showDetailsSheet = false
                            onReEnhance(asset)
                        },
                        style = QltyButtonStyle.Secondary,
                        modifier = Modifier.weight(1f),
                        testTag = "re_enhance_button"
                    )
                    QltyButton(
                        text = "Delete",
                        onClick = {
                            showDetailsSheet = false
                            onDeleteAsset(asset)
                        },
                        leadingIcon = Icons.Rounded.Delete,
                        style = QltyButtonStyle.Danger,
                        modifier = Modifier.weight(1f),
                        testTag = "delete_result_button"
                    )
                }
            }
        }
    }
}
