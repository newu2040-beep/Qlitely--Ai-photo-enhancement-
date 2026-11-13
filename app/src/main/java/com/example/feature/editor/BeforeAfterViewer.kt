package com.example.feature.editor

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.QltyColors
import com.example.core.designsystem.QltyRadius
import com.example.core.designsystem.QltySizes
import com.example.core.designsystem.QltySpace
import com.example.core.designsystem.QltyType
import com.example.core.domain.EnhancementAdjustments
import com.example.core.image.LocalEnhancementEngine
import kotlin.math.roundToInt

private class HorizontalFractionClipShape(private val fraction: Float) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val clamped = fraction.coerceIn(0f, 1f)
        return Outline.Rectangle(Rect(0f, 0f, size.width * clamped, size.height))
    }
}

@Composable
fun BeforeAfterViewer(
    originalBitmap: Bitmap?,
    processedBitmap: Bitmap?,
    adjustments: EnhancementAdjustments,
    isProcessing: Boolean,
    processingStage: String,
    modifier: Modifier = Modifier
) {
    var splitFraction by remember { mutableFloatStateOf(0.5f) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val previewColorFilter = remember(adjustments, processedBitmap) {
        if (processedBitmap != null) {
            null
        } else {
            val matrixValues = LocalEnhancementEngine.buildColorMatrixValues(adjustments)
            ColorFilter.colorMatrix(ColorMatrix(matrixValues))
        }
    }

    val totalVignette = remember(adjustments, processedBitmap) {
        if (processedBitmap != null) 0f
        else (adjustments.vignette + adjustments.filter.vignetteBoost).coerceIn(0f, 1f)
    }

    Surface(
        shape = QltyRadius.CardShape,
        color = QltyColors.Surface,
        border = BorderStroke(QltySizes.Hairline, QltyColors.Border),
        modifier = modifier
            .clip(QltyRadius.CardShape)
            .clipToBounds()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (zoomScale * zoom).coerceIn(1f, 4f)
                        zoomScale = newScale
                        if (newScale > 1f) {
                            val maxX = (size.width * (newScale - 1f)) / 2f
                            val maxY = (size.height * (newScale - 1f)) / 2f
                            panOffset = Offset(
                                x = (panOffset.x + pan.x).coerceIn(-maxX, maxX),
                                y = (panOffset.y + pan.y).coerceIn(-maxY, maxY)
                            )
                        } else {
                            panOffset = Offset.Zero
                        }
                    }
                }
        ) {
            val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
            val hasProcessed = processedBitmap != null

            // Right / After layer (full canvas)
            val afterBmp = processedBitmap ?: originalBitmap
            if (afterBmp != null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        bitmap = afterBmp.asImageBitmap(),
                        contentDescription = if (hasProcessed) "Enhanced photo" else "Adjustment preview",
                        contentScale = ContentScale.Fit,
                        colorFilter = previewColorFilter,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoomScale
                                scaleY = zoomScale
                                translationX = panOffset.x
                                translationY = panOffset.y
                            }
                    )
                    if (totalVignette > 0.02f) {
                        val edgeAlpha = (totalVignette * 0.72f).coerceIn(0f, 0.85f)
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val radius = maxOf(size.width, size.height) * 0.75f
                            drawRect(
                                brush = Brush.radialGradient(
                                    0.0f to Color.Transparent,
                                    0.45f to Color.Transparent,
                                    1.0f to Color.Black.copy(alpha = edgeAlpha),
                                    center = Offset(size.width / 2f, size.height / 2f),
                                    radius = radius
                                )
                            )
                        }
                    }
                }
            }

            // Left / Before layer (clipped to splitFraction at identical zoom & pan)
            if (originalBitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(HorizontalFractionClipShape(splitFraction))
                ) {
                    Image(
                        bitmap = originalBitmap.asImageBitmap(),
                        contentDescription = "Original photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoomScale
                                scaleY = zoomScale
                                translationX = panOffset.x
                                translationY = panOffset.y
                            }
                    )
                }
            }

            // Divider line + draggable circular handle
            val dividerOffsetX = (widthPx * splitFraction).roundToInt()
            Box(
                modifier = Modifier
                    .offset { IntOffset(dividerOffsetX - 24, 0) }
                    .width(QltySizes.TouchTarget)
                    .fillMaxHeight()
                    .pointerInput(widthPx) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            if (widthPx > 0f) {
                                splitFraction = (splitFraction + dragAmount.x / widthPx).coerceIn(0.05f, 0.95f)
                            }
                        }
                    }
                    .testTag("before_after_divider"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(QltyColors.OnOverlay.copy(alpha = 0.88f))
                )
                Surface(
                    shape = QltyRadius.PillShape,
                    color = QltyColors.Primary,
                    border = BorderStroke(QltySizes.Hairline, QltyColors.OnOverlay),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.CompareArrows,
                            contentDescription = "Drag to compare before and after",
                            tint = QltyColors.OnPrimary,
                            modifier = Modifier.size(QltySizes.IconChip)
                        )
                    }
                }
            }

            // Before / After corner pills
            Surface(
                shape = QltyRadius.PillShape,
                color = QltyColors.OverlayDark,
                border = BorderStroke(QltySizes.Hairline, QltyColors.Border),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(QltySpace.S12)
            ) {
                Text(
                    text = "Before",
                    style = QltyType.CaptionMedium,
                    color = QltyColors.OnOverlay,
                    modifier = Modifier.padding(horizontal = QltySpace.S12, vertical = QltySpace.S4)
                )
            }

            Surface(
                shape = QltyRadius.PillShape,
                color = QltyColors.OverlayDark,
                border = BorderStroke(QltySizes.Hairline, QltyColors.Border),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(QltySpace.S12)
            ) {
                Text(
                    text = if (hasProcessed) "After" else "Preview • ${adjustments.filter.label}",
                    style = QltyType.CaptionMedium,
                    color = if (hasProcessed) QltyColors.PrimaryHighlight else QltyColors.OnOverlay,
                    modifier = Modifier.padding(horizontal = QltySpace.S12, vertical = QltySpace.S4)
                )
            }

            if (zoomScale > 1.05f) {
                Surface(
                    shape = QltyRadius.PillShape,
                    color = QltyColors.OverlayDark,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(QltySpace.S8)
                ) {
                    Text(
                        text = String.format("%.1fx zoom", zoomScale),
                        style = QltyType.Caption,
                        color = QltyColors.OnOverlay,
                        modifier = Modifier.padding(horizontal = QltySpace.S12, vertical = QltySpace.S4)
                    )
                }
            }

            // Real active processing overlay
            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(QltyColors.OverlayDark),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(QltySpace.S16)
                    ) {
                        CircularProgressIndicator(
                            color = QltyColors.PrimaryHighlight,
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = processingStage.ifBlank { "Processing..." },
                            style = QltyType.BodyMedium,
                            color = QltyColors.OnOverlay,
                            modifier = Modifier.padding(top = QltySpace.S12)
                        )
                    }
                }
            }
        }
    }
}
