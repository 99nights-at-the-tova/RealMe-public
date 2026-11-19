package com.realme.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max

class ImageFileStore(private val context: Context) {
    suspend fun importImage(uri: Uri, folder: String = "imports"): String = withContext(Dispatchers.IO) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val largestSide = max(info.size.width, info.size.height)
            if (largestSide > MAX_IMAGE_SIDE) {
                val scale = MAX_IMAGE_SIDE.toFloat() / largestSide
                decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1),
                )
            }
        }
        try {
            saveBitmapOnIo(bitmap, folder)
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun copyToPostImage(sourcePath: String): String = withContext(Dispatchers.IO) {
        val source = ownedFile(sourcePath)
        require(source.isFile) { "選択した画像ファイルが見つかりません" }
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val largestSide = max(info.size.width, info.size.height)
            if (largestSide > MAX_IMAGE_SIDE) {
                val scale = MAX_IMAGE_SIDE.toFloat() / largestSide
                decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1),
                )
            }
        }
        try {
            saveBitmapOnIo(bitmap, "posts")
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun saveBitmap(bitmap: Bitmap, folder: String = "captures"): String =
        withContext(Dispatchers.IO) { saveBitmapOnIo(bitmap, folder) }

    /** アプリ管理下の画像を再エンコードせずに別フォルダへコピーする。 */
    suspend fun copyInto(sourcePath: String, folder: String = "captures"): String = withContext(Dispatchers.IO) {
        val source = ownedFile(sourcePath)
        require(source.isFile) { "画像ファイルが見つかりません" }
        val safeFolder = folder.filter { it.isLetterOrDigit() }.ifEmpty { "images" }
        val directory = File(context.filesDir, safeFolder).apply { mkdirs() }
        val output = File(directory, "${UUID.randomUUID()}.jpg")
        source.copyTo(output, overwrite = true)
        output.absolutePath
    }

    suspend fun delete(path: String?) = withContext(Dispatchers.IO) {
        if (path == null) return@withContext
        val file = ownedFile(path)
        if (file.isFile) check(file.delete()) { "画像ファイルを削除できませんでした" }
    }

    fun exists(path: String): Boolean = runCatching { ownedFile(path).isFile }.getOrDefault(false)

    private fun saveBitmapOnIo(bitmap: Bitmap, folder: String): String {
        val safeFolder = folder.filter { it.isLetterOrDigit() }.ifEmpty { "images" }
        val directory = File(context.filesDir, safeFolder).apply { mkdirs() }
        val output = File(directory, "${UUID.randomUUID()}.jpg")
        output.outputStream().buffered().use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) {
                "画像の保存に失敗しました"
            }
        }
        return output.absolutePath
    }

    private fun ownedFile(path: String): File {
        val root = context.filesDir.canonicalFile
        val file = File(path).canonicalFile
        require(file.path.startsWith(root.path + File.separator)) { "アプリ管理外の画像にはアクセスできません" }
        return file
    }

    private companion object {
        const val MAX_IMAGE_SIDE = 1600
        const val JPEG_QUALITY = 88
    }
}
