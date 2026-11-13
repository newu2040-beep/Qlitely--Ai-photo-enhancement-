package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.core.domain.AspectRatioOption
import com.example.core.domain.EnhancementAdjustments
import com.example.core.domain.ExportFormat
import com.example.core.domain.OutputResolution
import com.example.core.domain.PhotoFilter
import com.example.core.image.LocalEnhancementEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalEnhancementEngineTest {

    @Test
    fun `enhanceLocally produces real upscaled file and transforms pixel values`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()

            // Create a synthetic 120x80 underexposed gradient + edge test image on disk
            val srcBmp = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
            for (y in 0 until 80) {
                for (x in 0 until 120) {
                    val base = if (x in 40..80 && y in 20..60) 90 else 35
                    srcBmp.setPixel(x, y, Color.rgb(base, (base * 0.85f).toInt(), (base * 0.7f).toInt()))
                }
            }
            val inputFile = File(context.cacheDir, "test_input_dsp.png")
            FileOutputStream(inputFile).use { fos ->
                srcBmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            srcBmp.recycle()

            val stages = mutableListOf<String>()
            val result = LocalEnhancementEngine.enhanceLocally(
                context = context,
                originalPath = inputFile.absolutePath,
                aspectRatio = AspectRatioOption.ORIGINAL,
                resolution = OutputResolution.X2,
                customWidth = null,
                customHeight = null,
                adjustments = EnhancementAdjustments(
                    strength = 1.0f,
                    exposure = 0.35f,
                    contrastBoost = 0.40f,
                    shadows = 0.50f,
                    sharpen = 0.75f,
                    denoise = 0.35f,
                    vignette = 0.25f,
                    filter = PhotoFilter.VIVID
                ),
                format = ExportFormat.JPEG,
                jpegQuality = 92,
                onStage = { stages.add(it) }
            )

            assertTrue(
                "Expected enhanceLocally to succeed, got: ${result.exceptionOrNull()?.stackTraceToString()}",
                result.isSuccess
            )
            val output = result.getOrThrow()
            assertEquals(240, output.outputWidth)
            assertEquals(160, output.outputHeight)
            assertTrue(File(output.outputPath).length() > 0L)
            assertTrue(File(output.thumbnailPath).length() > 0L)
            assertTrue(output.processingTimeMs > 0L)
            assertTrue(stages.isNotEmpty())

            val outBmp = BitmapFactory.decodeFile(output.outputPath)
            val centerPixel = outBmp.getPixel(120, 80)
            // Brightened center region should significantly exceed original dark input (90, 76, 63)
            assertTrue(Color.red(centerPixel) > 105)
            outBmp.recycle()
            inputFile.delete()
        }
    }

    @Test
    fun `autoAnalyzeHistogram adapts dark photo with positive exposure and shadow lift`() {
        val darkBmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        darkBmp.eraseColor(Color.rgb(25, 22, 30))

        val tuned = LocalEnhancementEngine.autoAnalyzeHistogram(darkBmp, EnhancementAdjustments())
        darkBmp.recycle()

        assertTrue("Expected positive exposure lift for dark image", tuned.exposure > 0.1f)
        assertTrue("Expected positive shadow recovery for dark image", tuned.shadows > 0.2f)
    }

    @Test
    fun `noir filter converts chromatic image to monochrome`() {
        val colorBmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        colorBmp.eraseColor(Color.rgb(220, 40, 20))

        val noirBmp = LocalEnhancementEngine.applyColorFilterAndVignette(
            colorBmp,
            EnhancementAdjustments(filter = PhotoFilter.NOIR, strength = 1f)
        )
        colorBmp.recycle()

        val p = noirBmp.getPixel(16, 16)
        assertEquals(Color.red(p), Color.green(p))
        assertEquals(Color.green(p), Color.blue(p))
        assertNotEquals(220, Color.red(p))
        noirBmp.recycle()
    }
}
