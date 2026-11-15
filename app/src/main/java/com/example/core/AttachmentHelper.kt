package com.example.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

sealed interface PendingAttachment {
    val displayName: String

    data class ImageAttachment(
        override val displayName: String,
        val bitmap: Bitmap
    ) : PendingAttachment

    data class DocumentAttachment(
        override val displayName: String,
        val mimeType: String,
        val bytes: ByteArray,
        val extractedText: String? = null
    ) : PendingAttachment
}

object AttachmentHelper {
    private const val MAX_IMAGE_DIM = 1536
    private const val MAX_DOC_BYTES = 18 * 1024 * 1024 // Keep safely under Gemini 20MB inline limit

    suspend fun loadImageBitmap(context: Context, uri: Uri): PendingAttachment.ImageAttachment =
        withContext(Dispatchers.IO) {
            val rawBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val width = info.size.width
                    val height = info.size.height
                    val maxSide = maxOf(width, height)
                    if (maxSide > MAX_IMAGE_DIM) {
                        val scale = MAX_IMAGE_DIM.toFloat() / maxSide
                        decoder.setTargetSize(
                            (width * scale).toInt().coerceAtLeast(1),
                            (height * scale).toInt().coerceAtLeast(1)
                        )
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                } ?: throw IllegalStateException("Unable to open selected image.")
            }

            val scaled = scaleBitmapIfNeeded(rawBitmap, MAX_IMAGE_DIM)
            val name = queryDisplayName(context, uri) ?: "image_${System.currentTimeMillis()}.jpg"
            PendingAttachment.ImageAttachment(displayName = name, bitmap = scaled)
        }

    suspend fun loadDocument(context: Context, uri: Uri): PendingAttachment.DocumentAttachment =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri) ?: "application/octet-stream"
            val name = queryDisplayName(context, uri) ?: "document"

            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Unable to read selected file.")

            if (bytes.size > MAX_DOC_BYTES) {
                throw IllegalArgumentException(
                    "File '$name' is ${(bytes.size / (1024 * 1024))}MB, exceeding the 18MB inline analysis limit."
                )
            }

            val isText = mimeType.startsWith("text/") ||
                name.endsWith(".txt", ignoreCase = true) ||
                name.endsWith(".md", ignoreCase = true) ||
                name.endsWith(".csv", ignoreCase = true) ||
                name.endsWith(".json", ignoreCase = true) ||
                name.endsWith(".kt", ignoreCase = true) ||
                name.endsWith(".py", ignoreCase = true)

            val extractedText = if (isText) {
                bytes.toString(Charsets.UTF_8).take(120_000)
            } else {
                null
            }

            val normalizedMime = when {
                mimeType == "application/pdf" || name.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
                isText -> "text/plain"
                else -> mimeType
            }

            PendingAttachment.DocumentAttachment(
                displayName = name,
                mimeType = normalizedMime,
                bytes = bytes,
                extractedText = extractedText
            )
        }

    suspend fun saveGeneratedImage(context: Context, bitmap: Bitmap): String =
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "generated_images")
            if (!dir.exists()) dir.mkdirs()
            val fileName = "jarvis_img_${UUID.randomUUID().toString().take(8)}.png"
            val file = File(dir, fileName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
            }
            fileName
        }

    fun getGeneratedImageFile(context: Context, fileName: String): File? {
        if (fileName.isBlank()) return null
        val file = File(File(context.filesDir, "generated_images"), fileName)
        return if (file.exists()) file else null
    }

    fun getGeneratedPptDir(context: Context): File {
        val dir = File(context.filesDir, "presentations")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getGeneratedPptFile(context: Context, fileName: String): File? {
        if (fileName.isBlank()) return null
        val file = File(getGeneratedPptDir(context), fileName)
        return if (file.exists()) file else null
    }

    fun getShareUri(context: Context, file: File): Uri {
        return androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    suspend fun copyFileToUri(context: Context, source: File, destinationUri: Uri) =
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                source.inputStream().use { input ->
                    input.copyTo(out)
                }
            } ?: throw IllegalStateException("Unable to open destination file for saving.")
        }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / maxSide
        val targetW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val targetH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        } catch (_: Exception) {
            null
        }
    }
}
