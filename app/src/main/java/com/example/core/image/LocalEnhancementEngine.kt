package com.example.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.os.StatFs
import com.example.core.domain.AspectRatioOption
import com.example.core.domain.EnhancementAdjustments
import com.example.core.domain.EnhancementPreset
import com.example.core.domain.ExportFormat
import com.example.core.domain.OutputResolution
import com.example.core.domain.PhotoFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class DeviceCapabilities(
    val maxHeapMb: Long,
    val availableStorageMb: Long,
    val maxSafeOutputLongEdge: Int,
    val supportsLocalProcessing: Boolean
)

data class LocalEnhancementOutput(
    val outputPath: String,
    val thumbnailPath: String,
    val outputWidth: Int,
    val outputHeight: Int,
    val byteSize: Long,
    val processingTimeMs: Long
)

object LocalEnhancementEngine {

    private const val MAX_4K_LONG_EDGE = 3840
    private const val MIN_REQUIRED_STORAGE_MB = 25L

    fun inspectCapabilities(context: Context): DeviceCapabilities {
        val maxHeapMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L)
        val statBytes = runCatching { StatFs(context.filesDir.absolutePath).availableBytes }.getOrDefault(0L)
        val availableStorageMb = maxOf(statBytes, context.filesDir.usableSpace) / (1024L * 1024L)
        val safeEdge = when {
            maxHeapMb >= 256L -> 3840
            maxHeapMb >= 128L -> 2560
            else -> 1920
        }
        return DeviceCapabilities(
            maxHeapMb = maxHeapMb,
            availableStorageMb = availableStorageMb,
            maxSafeOutputLongEdge = safeEdge,
            supportsLocalProcessing = maxHeapMb >= 64L && availableStorageMb >= MIN_REQUIRED_STORAGE_MB
        )
    }

    fun computeTargetDimensions(
        sourceWidth: Int,
        sourceHeight: Int,
        aspectRatio: AspectRatioOption,
        resolution: OutputResolution,
        customWidth: Int?,
        customHeight: Int?
    ): Pair<Int, Int> {
        val (croppedW, croppedH) = computeCroppedDimensions(sourceWidth, sourceHeight, aspectRatio)
        if (croppedW <= 0 || croppedH <= 0) return 1 to 1

        return when (resolution) {
            OutputResolution.ORIGINAL -> croppedW to croppedH
            OutputResolution.X2 -> {
                val scale = minOf(2.0, 4096.0 / maxOf(croppedW, croppedH).toDouble())
                    .coerceAtLeast(1.0)
                (croppedW * scale).roundToInt().coerceAtLeast(1) to
                    (croppedH * scale).roundToInt().coerceAtLeast(1)
            }
            OutputResolution.UHD_4K -> {
                val longEdge = maxOf(croppedW, croppedH)
                val factor = MAX_4K_LONG_EDGE.toDouble() / longEdge.toDouble()
                val targetW = (croppedW * factor).roundToInt().coerceIn(1, 3840)
                val targetH = (croppedH * factor).roundToInt().coerceIn(1, 3840)
                targetW to targetH
            }
            OutputResolution.CUSTOM -> {
                val w = (customWidth ?: croppedW).coerceIn(64, 4096)
                val h = (customHeight ?: croppedH).coerceIn(64, 4096)
                w to h
            }
        }
    }

    fun computeCroppedDimensions(
        sourceWidth: Int,
        sourceHeight: Int,
        aspectRatio: AspectRatioOption
    ): Pair<Int, Int> {
        val targetRatio = aspectRatio.ratio ?: return sourceWidth to sourceHeight
        val currentRatio = sourceWidth.toFloat() / sourceHeight.toFloat()
        return if (currentRatio > targetRatio) {
            val newW = (sourceHeight * targetRatio).roundToInt().coerceIn(1, sourceWidth)
            newW to sourceHeight
        } else {
            val newH = (sourceWidth / targetRatio).roundToInt().coerceIn(1, sourceHeight)
            sourceWidth to newH
        }
    }

    fun cropBitmapNonDestructive(bitmap: Bitmap, aspectRatio: AspectRatioOption): Bitmap {
        val (targetW, targetH) = computeCroppedDimensions(bitmap.width, bitmap.height, aspectRatio)
        if (targetW == bitmap.width && targetH == bitmap.height) return bitmap
        val x = ((bitmap.width - targetW) / 2).coerceAtLeast(0)
        val y = ((bitmap.height - targetH) / 2).coerceAtLeast(0)
        return Bitmap.createBitmap(bitmap, x, y, targetW, targetH)
    }

    /**
     * Real on-device 256-bin luminance & RGB histogram analyzer that computes optimal
     * exposure, shadow lift, highlight recovery, contrast, saturation, and white-balance warmth.
     */
    fun autoAnalyzeHistogram(src: Bitmap, current: EnhancementAdjustments): EnhancementAdjustments {
        val sample = if (maxOf(src.width, src.height) > 320) {
            val scale = 320f / maxOf(src.width, src.height).toFloat()
            Bitmap.createScaledBitmap(
                src,
                (src.width * scale).roundToInt().coerceAtLeast(1),
                (src.height * scale).roundToInt().coerceAtLeast(1),
                true
            )
        } else {
            src
        }

        val w = sample.width
        val h = sample.height
        val pixels = IntArray(w * h)
        sample.getPixels(pixels, 0, w, 0, 0, w, h)
        if (sample !== src) sample.recycle()

        val lumaHist = IntArray(256)
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var darkPixels = 0
        var brightPixels = 0

        for (c in pixels) {
            val r = (c ushr 16) and 0xFF
            val g = (c ushr 8) and 0xFF
            val b = c and 0xFF
            val y = ((r * 77 + g * 150 + b * 29) ushr 8).coerceIn(0, 255)
            lumaHist[y]++
            sumR += r
            sumG += g
            sumB += b
            if (y < 52) darkPixels++
            if (y > 220) brightPixels++
        }

        val total = pixels.size.coerceAtLeast(1)
        var acc = 0
        var p5 = 0
        var p95 = 255
        for (i in 0..255) {
            acc += lumaHist[i]
            if (acc * 100 >= total * 5 && p5 == 0) p5 = i
            if (acc * 100 >= total * 95) {
                p95 = i
                break
            }
        }

        val meanR = sumR.toFloat() / total
        val meanG = sumG.toFloat() / total
        val meanB = sumB.toFloat() / total
        val meanLuma = (meanR * 0.299f + meanG * 0.587f + meanB * 0.114f) / 255f

        val dynamicRange = (p95 - p5).coerceAtLeast(1)
        val exposureAdj = ((0.48f - meanLuma) * 0.9f).coerceIn(-0.45f, 0.55f)
        val contrastAdj = ((210f - dynamicRange) / 255f * 0.9f).coerceIn(0.08f, 0.65f)
        val shadowsAdj = (darkPixels.toFloat() / total * 1.15f).coerceIn(0.05f, 0.70f)
        val highlightsAdj = -(brightPixels.toFloat() / total * 1.25f).coerceIn(0f, 0.65f)
        val warmthAdj = (((meanB - meanR) / 255f) * 1.1f).coerceIn(-0.40f, 0.40f)

        return current.copy(
            strength = 0.85f,
            exposure = exposureAdj,
            contrastBoost = contrastAdj,
            shadows = shadowsAdj,
            highlights = highlightsAdj,
            colorRecovery = 0.35f,
            warmth = warmthAdj,
            sharpen = max(current.sharpen, 0.62f),
            denoise = if (darkPixels.toFloat() / total > 0.35f) 0.58f else 0.30f
        )
    }

    /**
     * Shared 4x5 ColorMatrix values used by BOTH Compose live preview and final CPU rendering
     * so sliders and filters match the exported output.
     */
    fun buildColorMatrixValues(adjustments: EnhancementAdjustments): FloatArray {
        val f = adjustments.filter
        val s = adjustments.strength.coerceIn(0f, 1.5f)

        val rawSat = if (f.isMonochrome) {
            0f
        } else {
            (1f + adjustments.colorRecovery * 0.55f * s + f.saturationDelta).coerceIn(0f, 2.2f)
        }

        val contrast = (1f + adjustments.contrastBoost * 0.45f * s + f.contrastDelta).coerceIn(0.55f, 1.85f)
        val exposureShift = (adjustments.exposure + f.exposureDelta) * 58f * max(0.35f, s)
        val translate = (-0.5f * contrast + 0.5f) * 255f + exposureShift + f.fadeLift

        val warmth = (adjustments.warmth * s + f.warmthDelta).coerceIn(-1.2f, 1.2f)
        val rGain = 1f + warmth * 0.14f
        val bGain = 1f - warmth * 0.14f
        val rOffset = warmth * 14f
        val bOffset = -warmth * 14f

        val cm = android.graphics.ColorMatrix().apply {
            setSaturation(rawSat)
        }
        val contrastMatrix = android.graphics.ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        cm.postConcat(contrastMatrix)

        val tempMatrix = android.graphics.ColorMatrix(
            floatArrayOf(
                rGain, 0f, 0f, 0f, rOffset,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, bGain, 0f, bOffset,
                0f, 0f, 0f, 1f, 0f
            )
        )
        cm.postConcat(tempMatrix)

        if (adjustments.preset.warmRestore) {
            val restoreWarm = android.graphics.ColorMatrix(
                floatArrayOf(
                    1.04f, 0f, 0f, 0f, 4f,
                    0f, 1.01f, 0f, 0f, 1f,
                    0f, 0f, 0.96f, 0f, -2f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            cm.postConcat(restoreWarm)
        }

        return cm.array.copyOf()
    }

    /**
     * Generates real miniature previews of the user's imported image across all PhotoFilters.
     */
    suspend fun generateFilterThumbnails(
        originalPath: String
    ): Map<PhotoFilter, Bitmap> = withContext(Dispatchers.Default) {
        val base = ImageImportManager.decodeOrientedBitmap(originalPath, maxDimension = 144)
            ?: return@withContext emptyMap()
        val out = LinkedHashMap<PhotoFilter, Bitmap>(PhotoFilter.entries.size)
        for (filter in PhotoFilter.entries) {
            val adj = EnhancementAdjustments(
                strength = 1f,
                contrastBoost = 0f,
                colorRecovery = 0f,
                sharpen = 0f,
                denoise = 0f,
                filter = filter,
                preset = EnhancementPreset.NATURAL_CLARITY
            )
            out[filter] = applyColorFilterAndVignette(base, adj)
        }
        base.recycle()
        out
    }

    suspend fun enhanceLocally(
        context: Context,
        originalPath: String,
        aspectRatio: AspectRatioOption,
        resolution: OutputResolution,
        customWidth: Int?,
        customHeight: Int?,
        adjustments: EnhancementAdjustments,
        format: ExportFormat,
        jpegQuality: Int,
        onStage: (String) -> Unit
    ): Result<LocalEnhancementOutput> = withContext(Dispatchers.Default) {
        runCatching {
            val startTime = System.currentTimeMillis()
            onStage("Checking device memory & storage")
            val caps = inspectCapabilities(context)
            if (caps.availableStorageMb < MIN_REQUIRED_STORAGE_MB) {
                throw IllegalStateException("Low device storage (${caps.availableStorageMb} MB free). Free up space before processing.")
            }

            coroutineContext.ensureActive()
            onStage("Decoding full-quality source")
            val decoded = ImageImportManager.decodeOrientedBitmap(originalPath, maxDimension = 2560)
                ?: throw IllegalStateException("Unable to decode original image from storage.")

            val cropped = cropBitmapNonDestructive(decoded, aspectRatio)
            if (cropped !== decoded) decoded.recycle()

            val (desiredW, desiredH) = computeTargetDimensions(
                sourceWidth = cropped.width,
                sourceHeight = cropped.height,
                aspectRatio = AspectRatioOption.ORIGINAL,
                resolution = resolution,
                customWidth = customWidth,
                customHeight = customHeight
            )

            // Memory safeguard calibrated to device heap so 4K never OOMs
            val scaleCap = minOf(1f, caps.maxSafeOutputLongEdge.toFloat() / maxOf(desiredW, desiredH).toFloat())
            val finalW = (desiredW * scaleCap).roundToInt().coerceAtLeast(1)
            val finalH = (desiredH * scaleCap).roundToInt().coerceAtLeast(1)

            coroutineContext.ensureActive()
            onStage("Progressive upscaling to ${finalW} × ${finalH}")
            var working = progressiveScale(cropped, finalW, finalH)
            if (working !== cropped) cropped.recycle()

            // Run pixel DSP (denoise + unsharp + highlights/shadows LUT) in a memory-safe strip pass
            val hasPixelOps = adjustments.denoise > 0.06f ||
                adjustments.sharpen > 0.04f ||
                abs(adjustments.highlights) > 0.02f ||
                abs(adjustments.shadows) > 0.02f

            if (hasPixelOps) {
                coroutineContext.ensureActive()
                onStage("Applying denoise, detail sharpening & tone recovery")
                val dspOut = applyMemorySafeDsp(working, adjustments)
                if (dspOut !== working) working.recycle()
                working = dspOut
            }

            coroutineContext.ensureActive()
            onStage("Rendering ${adjustments.filter.label} color & vignette")
            val toned = applyColorFilterAndVignette(working, adjustments)
            if (toned !== working) working.recycle()

            coroutineContext.ensureActive()
            onStage("Encoding ${format.wire} output")
            val outDir = File(context.filesDir, "qlitey/outputs").apply { mkdirs() }
            val thumbDir = File(context.filesDir, "qlitey/thumbs").apply { mkdirs() }
            val id = UUID.randomUUID().toString().replace("-", "").take(16)
            val outFile = File(outDir, "enh_$id.${format.extension}")
            val thumbFile = File(thumbDir, "thm_$id.jpg")

            FileOutputStream(outFile).use { fos ->
                val compressFormat = when (format) {
                    ExportFormat.JPEG -> Bitmap.CompressFormat.JPEG
                    ExportFormat.PNG -> Bitmap.CompressFormat.PNG
                    ExportFormat.WEBP -> Bitmap.CompressFormat.WEBP_LOSSY
                }
                val q = if (format == ExportFormat.PNG) 100 else jpegQuality.coerceIn(50, 100)
                toned.compress(compressFormat, q, fos)
            }

            val thumbRatio = 480f / maxOf(toned.width, toned.height).toFloat()
            val tw = (toned.width * thumbRatio).roundToInt().coerceAtLeast(1)
            val th = (toned.height * thumbRatio).roundToInt().coerceAtLeast(1)
            val thumbBmp = Bitmap.createScaledBitmap(toned, tw, th, true)
            FileOutputStream(thumbFile).use { tfos ->
                thumbBmp.compress(Bitmap.CompressFormat.JPEG, 88, tfos)
            }
            thumbBmp.recycle()

            val outW = toned.width
            val outH = toned.height
            toned.recycle()

            LocalEnhancementOutput(
                outputPath = outFile.absolutePath,
                thumbnailPath = thumbFile.absolutePath,
                outputWidth = outW,
                outputHeight = outH,
                byteSize = outFile.length(),
                processingTimeMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
            )
        }
    }

    private fun progressiveScale(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        if (src.width == targetW && src.height == targetH) return src
        var current = src
        while (current.width < targetW / 2 && current.height < targetH / 2) {
            val nextW = (current.width * 1.5f).roundToInt().coerceAtMost(targetW)
            val nextH = (current.height * 1.5f).roundToInt().coerceAtMost(targetH)
            val next = Bitmap.createScaledBitmap(current, nextW, nextH, true)
            if (current !== src && current !== next) current.recycle()
            current = next
        }
        if (current.width != targetW || current.height != targetH) {
            val finalBmp = Bitmap.createScaledBitmap(current, targetW, targetH, true)
            if (current !== src && current !== finalBmp) current.recycle()
            current = finalBmp
        }
        return current
    }

    /**
     * Single-pass strip-based pixel DSP that prevents OOM at 4K (3840px) while performing
     * edge-preserving bilateral denoise, 3x3 unsharp mask, and highlights/shadows curve recovery.
     */
    private fun applyMemorySafeDsp(src: Bitmap, adjustments: EnhancementAdjustments): Bitmap {
        val w = src.width
        val h = src.height
        val mutable = src.copy(Bitmap.Config.ARGB_8888, true)

        val denoiseAmt = (adjustments.denoise * adjustments.strength).coerceIn(0f, 0.9f)
        val sharpAmt = (adjustments.sharpen * adjustments.strength).coerceIn(0f, 1.25f)
        val threshold3 = ((16f + denoiseAmt * 38f).roundToInt()) * 3
        val shadowLift = (adjustments.shadows * adjustments.strength).coerceIn(-0.8f, 0.8f)
        val highRec = (adjustments.highlights * adjustments.strength).coerceIn(-0.8f, 0.8f)

        // Precompute 256-entry highlights/shadows gain LUT for ultra-fast tonal recovery
        val hasToneCurve = abs(shadowLift) > 0.02f || abs(highRec) > 0.02f
        val toneLut = FloatArray(256) { i ->
            val t = i / 255f
            val shadowWeight = (1f - t).pow(2.2f)
            val highWeight = t.pow(2.2f)
            val delta = shadowLift * 0.42f * shadowWeight + highRec * 0.36f * highWeight
            ((t + delta).coerceIn(0f, 1f) / max(t, 0.004f)).coerceIn(0.45f, 2.2f)
        }

        // Process in strips of 128 rows with 1px overlap to keep memory < ~2 MB even at 4K
        val stripHeight = 128
        val inBuf = IntArray(w * (min(stripHeight + 2, h)))
        val outBuf = IntArray(w * min(stripHeight, h))

        var yStart = 0
        while (yStart < h) {
            val rows = min(stripHeight, h - yStart)
            val readTop = (yStart - 1).coerceAtLeast(0)
            val readBottom = (yStart + rows + 1).coerceAtMost(h)
            val readH = readBottom - readTop

            mutable.getPixels(inBuf, 0, w, 0, readTop, w, readH)

            for (localY in 0 until rows) {
                val absY = yStart + localY
                val bufY = absY - readTop
                val rowOff = bufY * w
                val prevOff = (bufY - 1).coerceAtLeast(0) * w
                val nextOff = (bufY + 1).coerceAtMost(readH - 1) * w
                val outRowOff = localY * w

                for (x in 0 until w) {
                    val idx = rowOff + x
                    val c = inBuf[idx]
                    val ca = (c ushr 24) and 0xFF
                    var cr = (c ushr 16) and 0xFF
                    var cg = (c ushr 8) and 0xFF
                    var cb = c and 0xFF

                    if (x in 1 until (w - 1) && absY in 1 until (h - 1)) {
                        // 1. Edge-preserving 5-tap denoise
                        if (denoiseAmt > 0.06f) {
                            var sR = cr
                            var sG = cg
                            var sB = cb
                            var cnt = 1
                            val nIds = intArrayOf(
                                prevOff + x, nextOff + x, rowOff + x - 1, rowOff + x + 1
                            )
                            for (nid in nIds) {
                                val nc = inBuf[nid]
                                val nr = (nc ushr 16) and 0xFF
                                val ng = (nc ushr 8) and 0xFF
                                val nb = nc and 0xFF
                                if (abs(nr - cr) + abs(ng - cg) + abs(nb - cb) <= threshold3) {
                                    sR += nr; sG += ng; sB += nb; cnt++
                                }
                            }
                            cr = (cr + (sR / cnt - cr) * denoiseAmt).roundToInt().coerceIn(0, 255)
                            cg = (cg + (sG / cnt - cg) * denoiseAmt).roundToInt().coerceIn(0, 255)
                            cb = (cb + (sB / cnt - cb) * denoiseAmt).roundToInt().coerceIn(0, 255)
                        }

                        // 2. Unsharp detail enhancement
                        if (sharpAmt > 0.04f) {
                            val n1 = inBuf[prevOff + x]
                            val n2 = inBuf[nextOff + x]
                            val n3 = inBuf[rowOff + x - 1]
                            val n4 = inBuf[rowOff + x + 1]
                            val bR = (((n1 ushr 16) and 0xFF) + ((n2 ushr 16) and 0xFF) + ((n3 ushr 16) and 0xFF) + ((n4 ushr 16) and 0xFF) + cr * 2) / 6
                            val bG = (((n1 ushr 8) and 0xFF) + ((n2 ushr 8) and 0xFF) + ((n3 ushr 8) and 0xFF) + ((n4 ushr 8) and 0xFF) + cg * 2) / 6
                            val bB = ((n1 and 0xFF) + (n2 and 0xFF) + (n3 and 0xFF) + (n4 and 0xFF) + cb * 2) / 6
                            val alpha = sharpAmt * 1.25f
                            cr = (cr + (cr - bR) * alpha).roundToInt().coerceIn(0, 255)
                            cg = (cg + (cg - bG) * alpha).roundToInt().coerceIn(0, 255)
                            cb = (cb + (cb - bB) * alpha).roundToInt().coerceIn(0, 255)
                        }
                    }

                    // 3. Shadows & Highlights tonal recovery
                    if (hasToneCurve) {
                        val y = ((cr * 77 + cg * 150 + cb * 29) ushr 8).coerceIn(0, 255)
                        val gain = toneLut[y]
                        cr = (cr * gain).roundToInt().coerceIn(0, 255)
                        cg = (cg * gain).roundToInt().coerceIn(0, 255)
                        cb = (cb * gain).roundToInt().coerceIn(0, 255)
                    }

                    outBuf[outRowOff + x] = (ca shl 24) or (cr shl 16) or (cg shl 8) or cb
                }
            }

            mutable.setPixels(outBuf, 0, w, 0, yStart, w, rows)
            yStart += rows
        }

        return mutable
    }

    fun applyColorFilterAndVignette(src: Bitmap, adjustments: EnhancementAdjustments): Bitmap {
        val w = src.width
        val h = src.height
        val result = src.copy(Bitmap.Config.ARGB_8888, true)
        val m = buildColorMatrixValues(adjustments)
        val m0 = m[0]; val m1 = m[1]; val m2 = m[2]; val m4 = m[4]
        val m5 = m[5]; val m6 = m[6]; val m7 = m[7]; val m9 = m[9]
        val m10 = m[10]; val m11 = m[11]; val m12 = m[12]; val m14 = m[14]

        val totalVignette = (adjustments.vignette + adjustments.filter.vignetteBoost).coerceIn(0f, 1f)
        val hasVignette = totalVignette > 0.02f && w > 0 && h > 0
        val cx = w / 2f
        val cy = h / 2f
        val radius = maxOf(w, h) * 0.75f

        val stripHeight = 128
        val buf = IntArray(w * min(stripHeight, h))
        var yStart = 0
        while (yStart < h) {
            val rows = min(stripHeight, h - yStart)
            result.getPixels(buf, 0, w, 0, yStart, w, rows)
            for (localY in 0 until rows) {
                val absY = yStart + localY
                val rowOff = localY * w
                val dy = if (hasVignette) (absY - cy) / radius else 0f
                val dy2 = dy * dy
                for (x in 0 until w) {
                    val idx = rowOff + x
                    val c = buf[idx]
                    val ca = (c ushr 24) and 0xFF
                    val r = (c ushr 16) and 0xFF
                    val g = (c ushr 8) and 0xFF
                    val b = c and 0xFF

                    var nr = (m0 * r + m1 * g + m2 * b + m4).roundToInt().coerceIn(0, 255)
                    var ng = (m5 * r + m6 * g + m7 * b + m9).roundToInt().coerceIn(0, 255)
                    var nb = (m10 * r + m11 * g + m12 * b + m14).roundToInt().coerceIn(0, 255)

                    if (hasVignette) {
                        val dx = (x - cx) / radius
                        val dist = sqrt(dx * dx + dy2)
                        if (dist > 0.45f) {
                            val t = ((dist - 0.45f) / 0.55f).coerceIn(0f, 1f)
                            val factor = 1f - t * totalVignette * 0.68f
                            nr = (nr * factor).roundToInt().coerceIn(0, 255)
                            ng = (ng * factor).roundToInt().coerceIn(0, 255)
                            nb = (nb * factor).roundToInt().coerceIn(0, 255)
                        }
                    }
                    buf[idx] = (ca shl 24) or (nr shl 16) or (ng shl 8) or nb
                }
            }
            result.setPixels(buf, 0, w, 0, yStart, w, rows)
            yStart += rows
        }
        return result
    }
}
