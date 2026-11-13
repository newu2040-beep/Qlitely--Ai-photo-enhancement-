package com.example.feature.editor

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.FilterVintage
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.data.EditorUiState
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
import com.example.core.domain.AspectRatioOption
import com.example.core.domain.EnhancementAdjustments
import com.example.core.domain.EnhancementPreset
import com.example.core.domain.ExportFormat
import com.example.core.domain.OutputResolution
import com.example.core.domain.PhotoFilter
import com.example.core.domain.ProcessingMode
import com.example.core.image.ImageImportManager
import com.example.core.image.LocalEnhancementEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class EditorToolSheet {
    FILTERS,
    ADJUST,
    EFFECTS,
    MORE
}

@Composable
fun EditorScreen(
    state: EditorUiState,
    onBack: () -> Unit,
    onSelectMode: (ProcessingMode) -> Unit,
    onSelectResolution: (OutputResolution) -> Unit,
    onSelectAspectRatio: (AspectRatioOption) -> Unit,
    onUpdateCustomDimensions: (Int?, Int?) -> Unit,
    onToggleAspectLock: (Boolean) -> Unit,
    onSelectFilter: (PhotoFilter) -> Unit,
    onApplyPreset: (EnhancementPreset) -> Unit,
    onUpdateAdjustments: ((EnhancementAdjustments) -> EnhancementAdjustments) -> Unit,
    onAutoTune: () -> Unit,
    onSelectFormat: (ExportFormat) -> Unit,
    onUpdateJpegQuality: (Int) -> Unit,
    onReset: () -> Unit,
    onAnalyzeWithCloud: () -> Unit,
    onEnhance: () -> Unit,
    onCancelProcessing: () -> Unit,
    onGrantCloudConsent: () -> Unit,
    onDismissCloudConsent: () -> Unit,
    onPickAnotherPhoto: () -> Unit,
    onBrowseFiles: () -> Unit
) {
    var activeSheet by remember { mutableStateOf<EditorToolSheet?>(null) }
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val meta = state.importedMeta

    LaunchedEffect(meta?.localOriginalPath) {
        val path = meta?.localOriginalPath
        if (path != null) {
            val bmp = withContext(Dispatchers.IO) {
                ImageImportManager.decodeOrientedBitmap(path, maxDimension = 1440)
            }
            originalBitmap?.recycle()
            originalBitmap = bmp
        }
    }

    LaunchedEffect(state.processedPreviewPath) {
        val path = state.processedPreviewPath
        if (path != null) {
            val bmp = withContext(Dispatchers.IO) {
                ImageImportManager.decodeOrientedBitmap(path, maxDimension = 1440)
            }
            processedBitmap?.recycle()
            processedBitmap = bmp
        } else {
            processedBitmap?.recycle()
            processedBitmap = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            originalBitmap?.recycle()
            processedBitmap?.recycle()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(QltyColors.Bg)
    ) {
        QltyTopBar(
            title = "Enhance Studio",
            subtitle = meta?.let { "${it.width} × ${it.height} • ${it.mimeType.substringAfter('/').uppercase()}" },
            navigationIcon = {
                QltyIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack,
                    testTag = "editor_back_button"
                )
            },
            actions = {
                QltyIconButton(
                    icon = Icons.Rounded.AutoAwesome,
                    contentDescription = "AI quality diagnosis",
                    onClick = onAnalyzeWithCloud,
                    enabled = meta != null && !state.isAnalyzing && !state.isProcessing,
                    testTag = "editor_analyze_button"
                )
                QltyIconButton(
                    icon = Icons.Rounded.MoreHoriz,
                    contentDescription = "Export and file options",
                    onClick = { activeSheet = EditorToolSheet.MORE },
                    testTag = "editor_more_button"
                )
            }
        )

        if (meta == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(QltySpace.S16),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(QltySpace.S12),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    QltyEmptyState(
                        icon = Icons.Rounded.AutoAwesome,
                        title = "No Photo Selected",
                        description = "Import a JPEG, PNG, WebP, or HEIC photo from Gallery or Files to start enhancing.",
                        actionLabel = "Pick from Gallery",
                        onAction = onPickAnotherPhoto
                    )
                    QltyButton(
                        text = "Browse Device Files (SAF)",
                        onClick = onBrowseFiles,
                        leadingIcon = Icons.Rounded.FolderOpen,
                        style = QltyButtonStyle.Secondary,
                        testTag = "editor_empty_browse_files"
                    )
                }
            }
            return@Column
        }

        val (targetW, targetH) = remember(
            meta.width,
            meta.height,
            state.aspectRatio,
            state.resolution,
            state.customWidth,
            state.customHeight
        ) {
            LocalEnhancementEngine.computeTargetDimensions(
                sourceWidth = meta.width,
                sourceHeight = meta.height,
                aspectRatio = state.aspectRatio,
                resolution = state.resolution,
                customWidth = state.customWidth,
                customHeight = state.customHeight
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 600.dp)
                .align(Alignment.CenterHorizontally)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = QltySpace.S16, vertical = QltySpace.S12),
            verticalArrangement = Arrangement.spacedBy(QltySpace.S16)
        ) {
            // Draggable Before / After photo preview
            BeforeAfterViewer(
                originalBitmap = originalBitmap,
                processedBitmap = processedBitmap,
                adjustments = state.adjustments,
                isProcessing = state.isProcessing || state.isAnalyzing || state.isAutoTuning,
                processingStage = when {
                    state.isAnalyzing -> "Analyzing photo with Gemini Vision..."
                    state.isAutoTuning -> "Balancing tone from histogram..."
                    else -> state.processingStage
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(272.dp)
            )

            // Quick studio pill actions row (Auto Tone, AI Diagnose, Active Filter, Reset)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(QltySpace.S8)
            ) {
                QltyChip(
                    label = "Auto Tone",
                    selected = false,
                    leadingIcon = Icons.Rounded.AutoFixHigh,
                    onClick = onAutoTune,
                    enabled = !state.isProcessing && !state.isAutoTuning,
                    testTag = "editor_quick_auto_tone"
                )
                QltyChip(
                    label = "Filter: ${state.adjustments.filter.label}",
                    selected = state.adjustments.filter != PhotoFilter.NATURAL,
                    leadingIcon = Icons.Rounded.FilterVintage,
                    onClick = { activeSheet = EditorToolSheet.FILTERS },
                    testTag = "editor_quick_filter_pill"
                )
                QltyChip(
                    label = "AI Diagnose",
                    selected = state.diagnosticReport != null,
                    leadingIcon = Icons.Rounded.AutoAwesome,
                    onClick = onAnalyzeWithCloud,
                    enabled = !state.isAnalyzing && !state.isProcessing,
                    testTag = "editor_quick_ai_diagnose"
                )
                QltyChip(
                    label = "Reset",
                    selected = false,
                    leadingIcon = Icons.Rounded.Refresh,
                    onClick = onReset,
                    enabled = !state.isProcessing,
                    testTag = "editor_quick_reset"
                )
            }

            // Cloud AI / Local AI Pill Segmented Control
            Surface(
                shape = QltyRadius.PillShape,
                color = QltyColors.Surface,
                border = BorderStroke(QltySizes.Hairline, QltyColors.Border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(QltySpace.S4),
                    horizontalArrangement = Arrangement.spacedBy(QltySpace.S4)
                ) {
                    ModeSegmentButton(
                        label = "Cloud AI",
                        icon = Icons.Rounded.Cloud,
                        selected = state.processingMode == ProcessingMode.CLOUD,
                        onClick = { onSelectMode(ProcessingMode.CLOUD) },
                        modifier = Modifier.weight(1f),
                        testTag = "mode_cloud_ai"
                    )
                    ModeSegmentButton(
                        label = "Local AI",
                        icon = Icons.Rounded.PhoneAndroid,
                        selected = state.processingMode == ProcessingMode.LOCAL,
                        onClick = { onSelectMode(ProcessingMode.LOCAL) },
                        modifier = Modifier.weight(1f),
                        testTag = "mode_local_ai"
                    )
                }
            }

            Text(
                text = if (state.processingMode == ProcessingMode.CLOUD) {
                    "Cloud AI • Requires network, Firebase Blaze quota & upload consent."
                } else {
                    "Local DSP • Real on-device denoise, unsharp mask, LUT tone & upscaling."
                },
                style = QltyType.Caption,
                color = QltyColors.TextMuted
            )

            // Gemini Vision diagnostic card if analyzed
            state.diagnosticReport?.let { report ->
                QltyCard {
                    Text(
                        text = "AI Diagnosis • ${report.sceneSummary}",
                        style = QltyType.BodyMedium,
                        color = QltyColors.Text
                    )
                    if (report.detectedIssues.isNotEmpty()) {
                        Text(
                            text = report.detectedIssues.joinToString(" • "),
                            style = QltyType.Caption,
                            color = QltyColors.PrimaryHighlight
                        )
                    }
                }
            }

            // Resolution Selector + Exact Target Dimensions Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Resolution",
                    style = QltyType.Heading,
                    color = QltyColors.Text
                )
                Surface(
                    shape = QltyRadius.PillShape,
                    color = QltyColors.Surface2,
                    border = BorderStroke(QltySizes.Hairline, QltyColors.Border)
                ) {
                    Text(
                        text = "$targetW × $targetH",
                        style = QltyType.CaptionMedium,
                        color = QltyColors.PrimaryHighlight,
                        modifier = Modifier.padding(horizontal = QltySpace.S12, vertical = QltySpace.S4)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(QltySpace.S8)
            ) {
                OutputResolution.entries.forEach { res ->
                    QltyChip(
                        label = res.label,
                        selected = state.resolution == res,
                        onClick = { onSelectResolution(res) },
                        testTag = "res_chip_${res.wire}"
                    )
                }
            }

            if (state.resolution == OutputResolution.CUSTOM) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(QltySpace.S12),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QltyInput(
                        value = (state.customWidth ?: targetW).toString(),
                        onValueChange = { txt -> onUpdateCustomDimensions(txt.toIntOrNull(), null) },
                        label = "Width (px)",
                        placeholder = "Width",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        testTag = "custom_width_input"
                    )
                    QltyInput(
                        value = (state.customHeight ?: targetH).toString(),
                        onValueChange = { txt -> onUpdateCustomDimensions(null, txt.toIntOrNull()) },
                        label = "Height (px)",
                        placeholder = "Height",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        testTag = "custom_height_input"
                    )
                }
            }

            // Aspect Ratio Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(QltySpace.S8)
            ) {
                AspectRatioOption.entries.forEach { ratio ->
                    QltyChip(
                        label = ratio.label,
                        selected = state.aspectRatio == ratio,
                        onClick = { onSelectAspectRatio(ratio) },
                        testTag = "ratio_chip_${ratio.label}"
                    )
                }
            }

            // Error Feedback & Graceful Alternative
            if (!state.errorMessage.isNullOrBlank()) {
                QltyCard {
                    Text(
                        text = state.errorMessage,
                        style = QltyType.Body,
                        color = QltyColors.Danger
                    )
                    if (state.processingMode == ProcessingMode.CLOUD) {
                        QltyButton(
                            text = "Switch to Local AI",
                            onClick = { onSelectMode(ProcessingMode.LOCAL) },
                            style = QltyButtonStyle.Secondary,
                            testTag = "fallback_local_ai_button"
                        )
                    }
                }
            }

            // Primary CTA: Enhance Image (single Primary button on screen)
            if (state.isProcessing) {
                QltyButton(
                    text = "Cancel Processing",
                    onClick = onCancelProcessing,
                    style = QltyButtonStyle.Danger,
                    testTag = "cancel_processing_button"
                )
            } else {
                QltyButton(
                    text = "Enhance Image",
                    onClick = onEnhance,
                    leadingIcon = Icons.Rounded.AutoAwesome,
                    style = QltyButtonStyle.Primary,
                    testTag = "enhance_image_button"
                )
            }

            Text(
                text = "${state.adjustments.preset.title} • ${state.adjustments.filter.label} • ${state.exportFormat.wire}",
                style = QltyType.Caption,
                color = QltyColors.TextMuted,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        // Rounded Pill Editor Bottom Tools Strip: Filters, Adjust, Effects, More
        Surface(
            color = QltyColors.Surface,
            border = BorderStroke(QltySizes.Hairline, QltyColors.Border),
            shape = QltyRadius.CardShape,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = QltySpace.S12, vertical = QltySpace.S8)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(QltySizes.BottomNavHeight)
                    .padding(horizontal = QltySpace.S4),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                EditorBottomTool(
                    icon = Icons.Rounded.FilterVintage,
                    label = "Filters",
                    active = activeSheet == EditorToolSheet.FILTERS || state.adjustments.filter != PhotoFilter.NATURAL,
                    onClick = { activeSheet = EditorToolSheet.FILTERS },
                    modifier = Modifier.weight(1f),
                    testTag = "tool_filters"
                )
                EditorBottomTool(
                    icon = Icons.Rounded.Tune,
                    label = "Adjust",
                    active = activeSheet == EditorToolSheet.ADJUST,
                    onClick = { activeSheet = EditorToolSheet.ADJUST },
                    modifier = Modifier.weight(1f),
                    testTag = "tool_adjust"
                )
                EditorBottomTool(
                    icon = Icons.Rounded.AutoAwesome,
                    label = "Effects",
                    active = activeSheet == EditorToolSheet.EFFECTS,
                    onClick = { activeSheet = EditorToolSheet.EFFECTS },
                    modifier = Modifier.weight(1f),
                    testTag = "tool_effects"
                )
                EditorBottomTool(
                    icon = Icons.Rounded.MoreHoriz,
                    label = "More",
                    active = activeSheet == EditorToolSheet.MORE,
                    onClick = { activeSheet = EditorToolSheet.MORE },
                    modifier = Modifier.weight(1f),
                    testTag = "tool_more"
                )
            }
        }
    }

    // Tool Sheets
    when (activeSheet) {
        EditorToolSheet.FILTERS -> {
            QltyBottomSheet(
                onDismissRequest = { activeSheet = null },
                title = "Studio Color Filters"
            ) {
                Text(
                    text = "Tap any filter to preview live on your photo and bake into export.",
                    style = QltyType.Caption,
                    color = QltyColors.TextMuted
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(QltySpace.S12)
                ) {
                    PhotoFilter.entries.forEach { filter ->
                        val isSelected = state.adjustments.filter == filter
                        val thumbBmp = state.filterThumbnails[filter] ?: originalBitmap
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(QltySpace.S8),
                            modifier = Modifier
                                .width(72.dp)
                                .clip(QltyRadius.CardShape)
                                .clickable { onSelectFilter(filter) }
                                .padding(vertical = QltySpace.S4)
                                .testTag("filter_thumb_${filter.id}")
                        ) {
                            Surface(
                                shape = QltyRadius.PillShape,
                                color = QltyColors.Surface2,
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else QltySizes.Hairline,
                                    color = if (isSelected) QltyColors.PrimaryHighlight else QltyColors.Border
                                ),
                                modifier = Modifier.size(60.dp)
                            ) {
                                if (thumbBmp != null && !thumbBmp.isRecycled) {
                                    Image(
                                        bitmap = thumbBmp.asImageBitmap(),
                                        contentDescription = filter.label,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.FilterVintage,
                                            contentDescription = filter.label,
                                            tint = QltyColors.PrimaryHighlight
                                        )
                                    }
                                }
                            }
                            Text(
                                text = filter.label,
                                style = if (isSelected) QltyType.CaptionMedium else QltyType.Caption,
                                color = if (isSelected) QltyColors.PrimaryHighlight else QltyColors.TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                QltyButton(
                    text = "Done",
                    onClick = { activeSheet = null },
                    style = QltyButtonStyle.Secondary,
                    testTag = "filters_done_button"
                )
            }
        }
        EditorToolSheet.EFFECTS -> {
            QltyBottomSheet(
                onDismissRequest = { activeSheet = null },
                title = "Restoration & AI Presets"
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(QltySpace.S8)
                ) {
                    EnhancementPreset.entries.forEach { preset ->
                        QltyListItem(
                            title = preset.title,
                            subtitle = preset.subtitle,
                            leadingIcon = Icons.Rounded.AutoAwesome,
                            trailingContent = {
                                if (state.adjustments.preset == preset) {
                                    Surface(
                                        shape = QltyRadius.PillShape,
                                        color = QltyColors.Primary.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "Active",
                                            style = QltyType.CaptionMedium,
                                            color = QltyColors.PrimaryHighlight,
                                            modifier = Modifier.padding(horizontal = QltySpace.S8, vertical = QltySpace.S4)
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onApplyPreset(preset)
                                activeSheet = null
                            },
                            testTag = "preset_${preset.id}"
                        )
                    }
                }
            }
        }
        EditorToolSheet.ADJUST -> {
            QltyBottomSheet(
                onDismissRequest = { activeSheet = null },
                title = "Pro Tone & Detail Suite"
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(QltySpace.S12)
                ) {
                    AdjustmentSlider(
                        label = "Master Strength",
                        value = state.adjustments.strength,
                        valueRange = 0f..1f,
                        onValueChange = { v -> onUpdateAdjustments { it.copy(strength = v) } }
                    )
                    AdjustmentSlider(
                        label = "Exposure",
                        value = state.adjustments.exposure,
                        valueRange = -1f..1f,
                        onValueChange = { v -> onUpdateAdjustments { it.copy(exposure = v) } }
                    )
                    AdjustmentSlider(
                        label = "Contrast",
                        value = state.adjustments.contrastBoost,
                        valueRange = -0.5f..1f,
                        onValueChange = { v -> onUpdateAdjustments { it.copy(contrastBoost = v) } }
                    )
                    AdjustmentSlider(
                        label = "Highlights Recovery",
                        value = state.adjustments.highlights,
                        valueRange = -1f..1f,
                        onValueChange = { v -> onUpdateAdjustments { it.copy(highlights = v) } }
                    )
                    AdjustmentSlider(
                        label = "Shadow Lift",
                        value = state.adjustments.shadows,
                        valueRange = -1f..1f,
                        onValueChange = { v -> onUpdateAdjustments { it.copy(shadows = v) } }
                    )
                    AdjustmentSlider(
                        label = "Saturation / Vibrance",
                        value = state.adjustments.colorRecovery,
                        valueRange = -1f..1f,
                        onValueChange = { v -> onUpdateAdjustments { it.copy(colorRecovery = v) } }
                    )
                    AdjustmentSlider(
                        label = "Warmth / Temperature",
                        value = state.adjustments.warmth,
                        valueRange = -1f..1f,
                        onValueChange = { v -> onUpdateAdjustments { it.copy(warmth = v) } }
                    )
                    AdjustmentSlider(
                        label = "Detail Sharpening",
                        value = state.adjustments.sharpen,
                        valueRange = 0f..1f,
                        onValueChange = { v -> onUpdateAdjustments { it.copy(sharpen = v) } }
                    )
                    AdjustmentSlider(
                        label = "Edge-Preserving Denoise",
                        value = state.adjustments.denoise,
                        valueRange = 0f..1f,
                        onValueChange = { v -> onUpdateAdjustments { it.copy(denoise = v) } }
                    )
                    AdjustmentSlider(
                        label = "Vignette",
                        value = state.adjustments.vignette,
                        valueRange = 0f..1f,
                        onValueChange = { v -> onUpdateAdjustments { it.copy(vignette = v) } }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(QltySpace.S8)
                ) {
                    QltyButton(
                        text = "Auto Tone",
                        onClick = onAutoTune,
                        style = QltyButtonStyle.Secondary,
                        leadingIcon = Icons.Rounded.AutoFixHigh,
                        modifier = Modifier.weight(1f),
                        testTag = "adjust_autotone_button"
                    )
                    QltyButton(
                        text = "Reset",
                        onClick = onReset,
                        style = QltyButtonStyle.Secondary,
                        leadingIcon = Icons.Rounded.Refresh,
                        modifier = Modifier.weight(1f),
                        testTag = "adjust_reset_button"
                    )
                }
                QltyButton(
                    text = "Done",
                    onClick = { activeSheet = null },
                    style = QltyButtonStyle.Secondary,
                    testTag = "adjust_done_button"
                )
            }
        }
        EditorToolSheet.MORE -> {
            QltyBottomSheet(
                onDismissRequest = { activeSheet = null },
                title = "Output, Format & Source"
            ) {
                Text("Export Format", style = QltyType.CaptionMedium, color = QltyColors.TextMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(QltySpace.S8)) {
                    ExportFormat.entries.forEach { fmt ->
                        QltyChip(
                            label = fmt.wire,
                            selected = state.exportFormat == fmt,
                            onClick = { onSelectFormat(fmt) },
                            testTag = "format_chip_${fmt.wire}"
                        )
                    }
                }
                Text(
                    text = when (state.exportFormat) {
                        ExportFormat.JPEG -> "JPEG (${state.jpegQuality}%): Compact file size, ideal for photos."
                        ExportFormat.PNG -> "PNG: Lossless encoding with alpha support; larger file size."
                        ExportFormat.WEBP -> "WebP (${state.jpegQuality}%): Modern compression with high clarity."
                    },
                    style = QltyType.Caption,
                    color = QltyColors.TextMuted
                )
                if (state.exportFormat != ExportFormat.PNG) {
                    AdjustmentSlider(
                        label = "Compression Quality (${state.jpegQuality}%)",
                        value = (state.jpegQuality - 50) / 50f,
                        valueRange = 0f..1f,
                        onValueChange = { v -> onUpdateJpegQuality((50 + v * 50).toInt()) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Lock Custom Aspect Ratio", style = QltyType.BodyMedium, color = QltyColors.Text)
                    Switch(
                        checked = state.aspectLock,
                        onCheckedChange = onToggleAspectLock,
                        colors = SwitchDefaults.colors(checkedTrackColor = QltyColors.Primary)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(QltySpace.S8)
                ) {
                    QltyButton(
                        text = "Gallery",
                        onClick = {
                            activeSheet = null
                            onPickAnotherPhoto()
                        },
                        leadingIcon = Icons.Rounded.PhotoLibrary,
                        style = QltyButtonStyle.Secondary,
                        modifier = Modifier.weight(1f),
                        testTag = "more_pick_gallery_button"
                    )
                    QltyButton(
                        text = "Browse Files",
                        onClick = {
                            activeSheet = null
                            onBrowseFiles()
                        },
                        leadingIcon = Icons.Rounded.FolderOpen,
                        style = QltyButtonStyle.Secondary,
                        modifier = Modifier.weight(1f),
                        testTag = "more_browse_files_button"
                    )
                }
                QltyButton(
                    text = "Reset All Settings",
                    onClick = {
                        onReset()
                        activeSheet = null
                    },
                    style = QltyButtonStyle.Secondary,
                    leadingIcon = Icons.Rounded.Refresh,
                    testTag = "more_reset_all_button"
                )
            }
        }
        null -> Unit
    }

    // Explicit Cloud AI Upload Consent Sheet
    if (state.showCloudConsentSheet) {
        QltyBottomSheet(
            onDismissRequest = onDismissCloudConsent,
            title = "Cloud AI Upload Consent"
        ) {
            QltyListItem(
                title = "Processed via Firebase AI Logic",
                subtitle = "Your cropped photo is transmitted securely to Google Gemini image enhancement models to generate the enhanced result.",
                leadingIcon = Icons.Rounded.Cloud
            )
            QltyListItem(
                title = "Original preserved locally",
                subtitle = "Your original photo remains untouched on-device. You can revoke Cloud AI consent anytime in Settings.",
                leadingIcon = Icons.Rounded.Info
            )
            QltyButton(
                text = "Agree & Enhance in Cloud",
                onClick = onGrantCloudConsent,
                style = QltyButtonStyle.Primary,
                testTag = "confirm_cloud_consent_button"
            )
            QltyButton(
                text = "Use Local AI Instead",
                onClick = {
                    onDismissCloudConsent()
                    onSelectMode(ProcessingMode.LOCAL)
                },
                style = QltyButtonStyle.Secondary,
                testTag = "decline_cloud_use_local_button"
            )
        }
    }
}

@Composable
private fun ModeSegmentButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    Surface(
        shape = QltyRadius.PillShape,
        color = Color.Transparent,
        modifier = modifier
            .height(QltySizes.ButtonHeight)
            .clip(QltyRadius.PillShape)
            .let { if (selected) it.background(QltyColors.PrimaryGradient) else it }
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = QltySpace.S12),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) QltyColors.OnPrimary else QltyColors.TextMuted,
                modifier = Modifier.size(QltySizes.IconChip)
            )
            Spacer(modifier = Modifier.width(QltySpace.S8))
            Text(
                text = label,
                style = QltyType.BodyMedium,
                color = if (selected) QltyColors.OnPrimary else QltyColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EditorBottomTool(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    Column(
        modifier = modifier
            .height(QltySizes.TouchTarget)
            .clip(QltyRadius.PillShape)
            .let { if (active) it.background(QltyColors.Primary.copy(alpha = 0.16f)) else it }
            .clickable(onClick = onClick)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) QltyColors.PrimaryHighlight else QltyColors.Text,
            modifier = Modifier.size(QltySizes.IconChip)
        )
        Text(
            text = label,
            style = QltyType.Caption,
            color = if (active) QltyColors.PrimaryHighlight else QltyColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AdjustmentSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    val pct = (value * 100).roundToInt()
    val formatted = if (valueRange.start < 0f && pct > 0) "+$pct%" else "$pct%"
    Column(verticalArrangement = Arrangement.spacedBy(QltySpace.S4)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = QltyType.BodyMedium, color = QltyColors.Text)
            Text(formatted, style = QltyType.CaptionMedium, color = QltyColors.PrimaryHighlight)
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            valueRange = valueRange,
            onValueChange = onValueChange,
            colors = SliderDefaults.colors(
                thumbColor = QltyColors.PrimaryHighlight,
                activeTrackColor = QltyColors.Primary,
                inactiveTrackColor = QltyColors.Surface2
            )
        )
    }
}
