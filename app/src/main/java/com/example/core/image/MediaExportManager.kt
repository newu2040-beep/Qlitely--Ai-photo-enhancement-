package com.example.core.image

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.core.domain.ExportFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

object MediaExportManager {

    suspend fun saveToGallery(
        context: Context,
        localOutputPath: String,
        displayName: String,
        format: ExportFormat
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val sourceFile = File(localOutputPath)
            if (!sourceFile.exists() || sourceFile.length() == 0L) {
                throw IllegalStateException("Processed output file is missing or expired.")
            }

            val stat = StatFs(Environment.getExternalStorageDirectory().absolutePath)
            if (stat.availableBytes < sourceFile.length() + (5L * 1024L * 1024L)) {
                throw IllegalStateException("Insufficient storage space to save to gallery.")
            }

            val cleanName = displayName
                .replace(Regex("[^a-zA-Z0-9_\\- ]"), "")
                .trim()
                .ifBlank { "QLITEY_${System.currentTimeMillis()}" }
            val filename = "${cleanName}.${format.extension}"

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/QLITEY")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Unable to create gallery entry.")

            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(out)
                }
            } ?: throw IllegalStateException("Unable to write image to gallery.")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val doneValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(uri, doneValues, null, null)
            }

            uri.toString()
        }
    }

    suspend fun saveCopyToUri(
        context: Context,
        localOutputPath: String,
        targetUri: Uri
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val sourceFile = File(localOutputPath)
            if (!sourceFile.exists() || sourceFile.length() == 0L) {
                throw IllegalStateException("Processed output file is missing or expired.")
            }
            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(out)
                }
            } ?: throw IllegalStateException("Unable to open chosen destination file.")
            Unit
        }
    }

    fun createShareIntent(
        context: Context,
        localOutputPath: String,
        format: ExportFormat,
        title: String
    ): Intent? {
        val file = File(localOutputPath)
        if (!file.exists()) return null
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = format.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun formatByteSize(bytes: Long): String {
        return when {
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
            else -> String.format("%.2f MB", bytes.toDouble() / (1024.0 * 1024.0))
        }
    }
}
