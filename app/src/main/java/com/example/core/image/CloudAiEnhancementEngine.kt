package com.example.core.image

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.core.domain.AspectRatioOption
import com.example.core.domain.EnhancementAdjustments
import com.example.core.domain.ExportFormat
import com.example.core.domain.OutputResolution
import com.example.core.domain.PhotoFilter
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

data class PhotoDiagnosticReport(
    val sceneSummary: String,
    val detectedIssues: List<String>,
    val recommendedPresetId: String,
    val recommendedDenoise: Float,
    val recommendedSharpen: Float,
    val recommendedColorRecovery: Float,
    val recommendedExposure: Float = 0f,
    val recommendedContrast: Float = 0.30f
)

object CloudAiEnhancementEngine {

    private const val TAG = "CloudAiEngine"
    const val MODEL_DIAGNOSTIC = "gemini-3.8-flash"
    const val MODEL_IMAGE_ENHANCE = "gemini-nano-banana-2.1"

    private val diagnosticSchema = Schema.obj(
        properties = mapOf(
            "sceneSummary" to Schema.string(description = "Brief 1-sentence description of the photo subject and lighting."),
            "detectedIssues" to Schema.array(
                items = Schema.string(),
                description = "Up to 3 concrete image quality issues such as low-light grain, compression artifacts, soft focus, underexposure, or faded colors."
            ),
            "recommendedPresetId" to Schema.enumeration(
                values = listOf("ai_enhance", "upscale_4k", "restore_old", "natural_clarity", "low_light_denoise"),
                description = "Best matching QLITEY enhancement preset ID."
            ),
            "recommendedDenoise" to Schema.double(description = "Recommended denoise level from 0.0 to 1.0"),
            "recommendedSharpen" to Schema.double(description = "Recommended sharpen level from 0.0 to 1.0"),
            "recommendedColorRecovery" to Schema.double(description = "Recommended color saturation recovery from -0.5 to 1.0"),
            "recommendedExposure" to Schema.double(description = "Recommended exposure compensation from -0.6 to 0.6"),
            "recommendedContrast" to Schema.double(description = "Recommended contrast boost from 0.0 to 0.8")
        )
    )

    private val diagnosticModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_DIAGNOSTIC,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = diagnosticSchema
            }
        )
    }

    private val imageEnhanceModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_IMAGE_ENHANCE,
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    suspend fun analyzePhoto(originalPath: String): Result<PhotoDiagnosticReport> =
        withContext(Dispatchers.IO) {
            try {
                val previewBmp = ImageImportManager.decodeOrientedBitmap(originalPath, maxDimension = 1024)
                    ?: return@withContext Result.failure(IllegalStateException("Unable to decode image for analysis."))
                val response = try {
                    withTimeout(45_000L) {
                        diagnosticModel.generateContent(
                            content {
                                image(previewBmp)
                                text(
                                    "Inspect this photograph's technical image quality (noise, compression, sharpness, exposure, highlights, shadows, fading) and recommend QLITEY enhancement parameters."
                                )
                            }
                        )
                    }
                } finally {
                    previewBmp.recycle()
                }

                val jsonText = response.text ?: throw IllegalStateException("Diagnostic model returned empty response.")
                val json = JSONObject(jsonText)
                val issuesJson = json.optJSONArray("detectedIssues")
                val issues = buildList {
                    if (issuesJson != null) {
                        for (i in 0 until issuesJson.length()) {
                            add(issuesJson.optString(i))
                        }
                    }
                }.filter { it.isNotBlank() }

                Result.success(
                    PhotoDiagnosticReport(
                        sceneSummary = json.optString("sceneSummary", "Photo analyzed."),
                        detectedIssues = issues,
                        recommendedPresetId = json.optString("recommendedPresetId", "ai_enhance"),
                        recommendedDenoise = json.optDouble("recommendedDenoise", 0.45).toFloat().coerceIn(0f, 1f),
                        recommendedSharpen = json.optDouble("recommendedSharpen", 0.60).toFloat().coerceIn(0f, 1f),
                        recommendedColorRecovery = json.optDouble("recommendedColorRecovery", 0.45).toFloat().coerceIn(-0.5f, 1f),
                        recommendedExposure = json.optDouble("recommendedExposure", 0.0).toFloat().coerceIn(-0.6f, 0.6f),
                        recommendedContrast = json.optDouble("recommendedContrast", 0.30).toFloat().coerceIn(0f, 0.8f)
                    )
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Cloud AI analysis timed out", e)
                Result.failure(IllegalStateException("Cloud AI analysis timed out after 45 seconds."))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Cloud AI analysis failed", e)
                Result.failure(e)
            }
        }

    suspend fun enhanceInCloud(
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
    ): Result<LocalEnhancementOutput> = withContext(Dispatchers.IO) {
        try {
            val startTime = System.currentTimeMillis()
            onStage("Preparing upload payload")
            val sourceBmp = ImageImportManager.decodeOrientedBitmap(originalPath, maxDimension = 1536)
                ?: throw IllegalStateException("Unable to decode source photo for Cloud AI.")
            val croppedInput = LocalEnhancementEngine.cropBitmapNonDestructive(sourceBmp, aspectRatio)
            if (croppedInput !== sourceBmp) sourceBmp.recycle()

            val (targetW, targetH) = LocalEnhancementEngine.computeTargetDimensions(
                sourceWidth = croppedInput.width,
                sourceHeight = croppedInput.height,
                aspectRatio = AspectRatioOption.ORIGINAL,
                resolution = resolution,
                customWidth = customWidth,
                customHeight = customHeight
            )

            val prompt = buildString {
                append("Restore, denoise, deblur, and enhance this photograph cleanly while preserving the exact subject, facial identity, composition, and framing. ")
                append("Preset style: ${adjustments.preset.title}. ")
                if (adjustments.filter != PhotoFilter.NATURAL) {
                    append("Apply subtle ${adjustments.filter.label} color grading. ")
                }
                append("Strength ${(adjustments.strength * 100).roundToInt()}%, ")
                append("noise reduction ${(adjustments.denoise * 100).roundToInt()}%, ")
                append("detail sharpening ${(adjustments.sharpen * 100).roundToInt()}%, ")
                append("color vibrancy ${(adjustments.colorRecovery * 100).roundToInt()}%. ")
                if (abs(adjustments.exposure) > 0.05f) {
                    append("Exposure balance ${if (adjustments.exposure > 0) "brighter" else "darker"} (${(adjustments.exposure * 100).roundToInt()}%). ")
                }
                if (adjustments.shadows > 0.1f) {
                    append("Recover shadow detail cleanly. ")
                }
                if (adjustments.highlights < -0.1f) {
                    append("Recover bright highlights without clipping. ")
                }
                if (adjustments.preset.warmRestore) {
                    append("Repair scratches, faded contrast, and color cast gently without altering faces or landmarks. ")
                }
                append("Do not crop, add borders, watermarks, text, or invent unrelated objects.")
            }

            onStage("Running Cloud AI enhancement (up to 60s)")
            val response = try {
                withTimeout(60_000L) {
                    imageEnhanceModel.generateContent(
                        content {
                            image(croppedInput)
                            text(prompt)
                        }
                    )
                }
            } finally {
                croppedInput.recycle()
            }

            val cloudBmp = response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<ImagePart>()
                ?.firstOrNull()?.image
                ?: throw IllegalStateException("Cloud AI did not return an enhanced image.")

            onStage("Finalizing ${targetW} × ${targetH} ${format.wire} output")
            val caps = LocalEnhancementEngine.inspectCapabilities(context)
            val scaleCap = minOf(1f, caps.maxSafeOutputLongEdge.toFloat() / maxOf(targetW, targetH).toFloat())
            val finalW = (targetW * scaleCap).roundToInt().coerceAtLeast(1)
            val finalH = (targetH * scaleCap).roundToInt().coerceAtLeast(1)

            val scaledBmp = if (cloudBmp.width != finalW || cloudBmp.height != finalH) {
                Bitmap.createScaledBitmap(cloudBmp, finalW, finalH, true).also {
                    if (it !== cloudBmp) cloudBmp.recycle()
                }
            } else {
                cloudBmp
            }

            // Apply vignette / fine monochrome if requested cleanly
            val totalVig = (adjustments.vignette + adjustments.filter.vignetteBoost).coerceIn(0f, 1f)
            val finalBmp = if (totalVig > 0.02f || adjustments.filter.isMonochrome) {
                LocalEnhancementEngine.applyColorFilterAndVignette(
                    scaledBmp,
                    adjustments.copy(
                        exposure = 0f,
                        contrastBoost = 0f,
                        colorRecovery = 0f,
                        warmth = 0f
                    )
                ).also {
                    if (it !== scaledBmp) scaledBmp.recycle()
                }
            } else {
                scaledBmp
            }

            val outDir = File(context.filesDir, "qlitey/outputs").apply { mkdirs() }
            val thumbDir = File(context.filesDir, "qlitey/thumbs").apply { mkdirs() }
            val id = UUID.randomUUID().toString().replace("-", "").take(16)
            val outFile = File(outDir, "cloud_$id.${format.extension}")
            val thumbFile = File(thumbDir, "thm_$id.jpg")

            FileOutputStream(outFile).use { fos ->
                val compressFormat = when (format) {
                    ExportFormat.JPEG -> Bitmap.CompressFormat.JPEG
                    ExportFormat.PNG -> Bitmap.CompressFormat.PNG
                    ExportFormat.WEBP -> Bitmap.CompressFormat.WEBP_LOSSY
                }
                val q = if (format == ExportFormat.PNG) 100 else jpegQuality.coerceIn(50, 100)
                finalBmp.compress(compressFormat, q, fos)
            }

            val thumbRatio = 480f / maxOf(finalBmp.width, finalBmp.height).toFloat()
            val tw = (finalBmp.width * thumbRatio).roundToInt().coerceAtLeast(1)
            val th = (finalBmp.height * thumbRatio).roundToInt().coerceAtLeast(1)
            val thumbBmp = Bitmap.createScaledBitmap(finalBmp, tw, th, true)
            FileOutputStream(thumbFile).use { tfos ->
                thumbBmp.compress(Bitmap.CompressFormat.JPEG, 88, tfos)
            }
            thumbBmp.recycle()

            val outW = finalBmp.width
            val outH = finalBmp.height
            finalBmp.recycle()

            Result.success(
                LocalEnhancementOutput(
                    outputPath = outFile.absolutePath,
                    thumbnailPath = thumbFile.absolutePath,
                    outputWidth = outW,
                    outputHeight = outH,
                    byteSize = outFile.length(),
                    processingTimeMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Cloud AI enhancement timed out", e)
            Result.failure(IllegalStateException("Cloud AI timed out after 60 seconds. Retry or use Local AI."))
        } catch (e: QuotaExceededException) {
            Log.e(TAG, "Cloud AI quota exceeded", e)
            Result.failure(IllegalStateException("Cloud AI image editing requires a Firebase Blaze billing plan or quota. Switch to Local AI to process on-device."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Cloud AI enhancement failed", e)
            Result.failure(
                IllegalStateException(
                    e.localizedMessage ?: "Cloud AI enhancement failed. Verify Blaze billing/network or use Local AI."
                )
            )
        }
    }
}
