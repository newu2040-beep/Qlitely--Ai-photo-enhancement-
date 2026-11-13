package com.example.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import com.example.core.domain.ImportedImageMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

object ImageImportManager {

    val SUPPORTED_MIME_TYPES = setOf(
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/webp",
        "image/heif",
        "image/heic"
    )

    const val MAX_SOURCE_BYTES = 35L * 1024L * 1024L // 35 MB safeguard

    sealed interface ImportResult {
        data class Success(val meta: ImportedImageMeta) : ImportResult
        data class UnsupportedFormat(val mimeType: String) : ImportResult
        data class TooLarge(val byteSize: Long, val maxBytes: Long) : ImportResult
        data class Error(val message: String) : ImportResult
    }

    suspend fun importFromUri(context: Context, uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val rawMime = resolver.getType(uri)?.lowercase() ?: "image/jpeg"
            val isKnownMime = rawMime in SUPPORTED_MIME_TYPES || rawMime == "image/*" || rawMime == "application/octet-stream"
            if (!isKnownMime) {
                return@withContext ImportResult.UnsupportedFormat(rawMime)
            }

            var displayName = "photo_${System.currentTimeMillis()}"
            var reportedSize = -1L
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        displayName = cursor.getString(nameIdx) ?: displayName
                    }
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        reportedSize = cursor.getLong(sizeIdx)
                    }
                }
            }

            if (reportedSize > MAX_SOURCE_BYTES) {
                return@withContext ImportResult.TooLarge(reportedSize, MAX_SOURCE_BYTES)
            }

            val originalsDir = File(context.filesDir, "qlitey/originals").apply { mkdirs() }
            val ext = when {
                rawMime.contains("png") -> "png"
                rawMime.contains("webp") -> "webp"
                else -> "jpg"
            }
            val destFile = File(originalsDir, "orig_${UUID.randomUUID().toString().take(12)}.$ext")

            val digest = MessageDigest.getInstance("SHA-256")
            var totalBytes = 0L
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        totalBytes += read
                        if (totalBytes > MAX_SOURCE_BYTES) {
                            output.close()
                            destFile.delete()
                            return@withContext ImportResult.TooLarge(totalBytes, MAX_SOURCE_BYTES)
                        }
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                }
            } ?: return@withContext ImportResult.Error("Unable to open the selected photo stream.")

            if (totalBytes == 0L) {
                destFile.delete()
                return@withContext ImportResult.Error("Selected file appears to be empty or corrupt.")
            }

            // Read bounds and actual decoded MIME type
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(destFile.absolutePath, bounds)
            val resolvedMime = bounds.outMimeType?.lowercase() ?: rawMime
            if (resolvedMime !in SUPPORTED_MIME_TYPES) {
                destFile.delete()
                return@withContext ImportResult.UnsupportedFormat(resolvedMime)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                destFile.delete()
                return@withContext ImportResult.Error("Could not decode image dimensions. File may be corrupted.")
            }

            val exifRotation = readExifRotation(destFile)
            val effectiveWidth = if (exifRotation == 90 || exifRotation == 270) bounds.outHeight else bounds.outWidth
            val effectiveHeight = if (exifRotation == 90 || exifRotation == 270) bounds.outWidth else bounds.outHeight

            val checksum = digest.digest().joinToString("") { "%02x".format(it) }
            val cleanTitle = displayName.substringBeforeLast('.').ifBlank { "Enhanced Photo" }

            ImportResult.Success(
                ImportedImageMeta(
                    sourceUriString = uri.toString(),
                    localOriginalPath = destFile.absolutePath,
                    displayName = cleanTitle,
                    width = effectiveWidth,
                    height = effectiveHeight,
                    mimeType = resolvedMime,
                    byteSize = totalBytes,
                    checksumSha256 = checksum,
                    hasAlpha = resolvedMime.contains("png") || resolvedMime.contains("webp")
                )
            )
        } catch (e: SecurityException) {
            ImportResult.Error("Permission to read this photo was revoked: ${e.localizedMessage}")
        } catch (e: Exception) {
            ImportResult.Error(e.localizedMessage ?: "Failed to import photo.")
        }
    }

    fun decodeOrientedBitmap(filePath: String, maxDimension: Int = 2048): Bitmap? {
        val file = File(filePath)
        if (!file.exists()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(filePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxSide / sampleSize > maxDimension) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = BitmapFactory.decodeFile(filePath, decodeOpts) ?: return null
        val rotation = readExifRotation(file)
        if (rotation == 0) return raw

        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        if (rotated !== raw) raw.recycle()
        return rotated
    }

    private fun readExifRotation(file: File): Int {
        return try {
            val exif = ExifInterface(file.absolutePath)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (_: Exception) {
            0
        }
    }
}
