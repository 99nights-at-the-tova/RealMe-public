package com.realme.feature.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Paint
import androidx.compose.runtime.Immutable
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.withClip
import androidx.core.graphics.withRotation
import com.realme.data.local.ImageFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class EditorFilter(val label: String) {
    ORIGINAL("標準"),
    MONO("モノクロ"),
    WARM("ウォーム"),
    COOL("クール"),
}

enum class CropRatio(val label: String) {
    ORIGINAL("切抜なし"),
    SQUARE("正方形"),
    FOUR_THREE("4:3"),
    FREE("自由"),
}

enum class EditorEffect(val label: String) {
    NONE("効果なし"),
    BLUR("ぼかし"),
    MOSAIC("モザイク"),
}

@Immutable
data class NormalizedRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
) {
    val isFull: Boolean
        get() = left <= 0.001f && top <= 0.001f && right >= 0.999f && bottom >= 0.999f
}

@Immutable
data class EditorText(
    val id: Long,
    val text: String,
    val xFraction: Float,
    val yFraction: Float,
    val colorIndex: Int,
)

@Immutable
data class EditorStamp(
    val id: Long,
    val symbol: String,
    val xFraction: Float,
    val yFraction: Float,
    val scale: Float = 1f,
    val rotation: Float = 0f,
)

@Immutable
data class EditorConfig(
    val filter: EditorFilter = EditorFilter.ORIGINAL,
    val cropRatio: CropRatio = CropRatio.ORIGINAL,
    val freeCrop: NormalizedRect = NormalizedRect(),
    val effect: EditorEffect = EditorEffect.NONE,
    val effectRect: NormalizedRect = NormalizedRect(),
    val texts: List<EditorText> = emptyList(),
    val stamps: List<EditorStamp> = emptyList(),
)

class ImageEditorEngine(private val images: ImageFileStore) {
    suspend fun load(path: String): Bitmap = withContext(Dispatchers.IO) {
        val source = ImageDecoder.createSource(File(path))
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val largest = max(info.size.width, info.size.height)
            if (largest > MAX_SIDE) {
                val scale = MAX_SIDE.toFloat() / largest
                decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1),
                )
            }
        }
    }

    suspend fun renderBase(source: Bitmap, config: EditorConfig): Bitmap =
        withContext(Dispatchers.Default) {
            var current = crop(source, config)
            if (config.filter != EditorFilter.ORIGINAL) {
                current = applyFilter(current, config.filter)
            }
            if (config.effect != EditorEffect.NONE) {
                current = applyRegionEffect(current, config.effect, config.effectRect)
            }
            current
        }

    suspend fun renderFinal(source: Bitmap, config: EditorConfig): Bitmap =
        withContext(Dispatchers.Default) {
            val base = renderBase(source, config)
            val output = base.copy(Bitmap.Config.ARGB_8888, true)
            if (base !== output) base.recycle()
            drawOverlays(Canvas(output), output.width, output.height, config)
            output
        }

    suspend fun save(bitmap: Bitmap): String = images.saveBitmap(bitmap, "edits")

    private fun crop(source: Bitmap, config: EditorConfig): Bitmap {
        if (config.cropRatio == CropRatio.ORIGINAL) {
            return source.copy(Bitmap.Config.ARGB_8888, false)
        }
        val x: Int
        val y: Int
        val width: Int
        val height: Int
        if (config.cropRatio == CropRatio.FREE) {
            val normalized = config.freeCrop
            x = (source.width * normalized.left).roundToInt().coerceIn(0, source.width - 1)
            y = (source.height * normalized.top).roundToInt().coerceIn(0, source.height - 1)
            width = (source.width * (normalized.right - normalized.left)).roundToInt()
                .coerceIn(1, source.width - x)
            height = (source.height * (normalized.bottom - normalized.top)).roundToInt()
                .coerceIn(1, source.height - y)
        } else {
            val targetRatio = if (config.cropRatio == CropRatio.SQUARE) 1f else 4f / 3f
            var cropWidth = source.width
            var cropHeight = source.height
            if (cropWidth.toFloat() / cropHeight > targetRatio) {
                cropWidth = (cropHeight * targetRatio).toInt()
            } else {
                cropHeight = (cropWidth / targetRatio).toInt()
            }
            width = cropWidth
            height = cropHeight
            x = (source.width - width) / 2
            y = (source.height - height) / 2
        }
        val cropped = Bitmap.createBitmap(source, x, y, width, height)
        return if (cropped === source) source.copy(Bitmap.Config.ARGB_8888, false) else cropped
    }

    private fun applyRegionEffect(
        source: Bitmap,
        effect: EditorEffect,
        region: NormalizedRect,
    ): Bitmap {
        val block = when (effect) {
            EditorEffect.BLUR -> 18 to true
            EditorEffect.MOSAIC -> 14 to false
            EditorEffect.NONE -> return source
        }
        val left = (source.width * region.left).toInt().coerceIn(0, source.width - 1)
        val top = (source.height * region.top).toInt().coerceIn(0, source.height - 1)
        val right = (source.width * region.right).toInt().coerceIn(left + 1, source.width)
        val bottom = (source.height * region.bottom).toInt().coerceIn(top + 1, source.height)
        if (region.isFull) {
            return pixelate(source, block.first, block.second)
        }
        val effected = pixelate(source.copy(Bitmap.Config.ARGB_8888, false), block.first, block.second)
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        try {
            Canvas(output).withClip(
                left.toFloat(),
                top.toFloat(),
                right.toFloat(),
                bottom.toFloat(),
            ) {
                drawBitmap(effected, 0f, 0f, null)
            }
            return output
        } finally {
            source.recycle()
            effected.recycle()
        }
    }

    private fun applyFilter(source: Bitmap, filter: EditorFilter): Bitmap {
        val matrix = when (filter) {
            EditorFilter.MONO -> ColorMatrix().apply { setSaturation(0f) }
            EditorFilter.WARM -> ColorMatrix(
                floatArrayOf(
                    1.12f, 0f, 0f, 0f, 10f,
                    0f, 1.02f, 0f, 0f, 4f,
                    0f, 0f, 0.88f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            )
            EditorFilter.COOL -> ColorMatrix(
                floatArrayOf(
                    0.9f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 2f,
                    0f, 0f, 1.14f, 0f, 8f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            )
            EditorFilter.ORIGINAL -> ColorMatrix()
        }
        val output = createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Canvas(output).drawBitmap(
            source,
            0f,
            0f,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(matrix)
            },
        )
        source.recycle()
        return output
    }

    private fun pixelate(source: Bitmap, block: Int, smooth: Boolean): Bitmap {
        val smallWidth = (source.width / block).coerceAtLeast(1)
        val smallHeight = (source.height / block).coerceAtLeast(1)
        val small = source.scale(smallWidth, smallHeight, true)
        if (small === source) return source
        val output = small.scale(source.width, source.height, smooth)
        if (small !== output) small.recycle()
        if (output !== source) source.recycle()
        return output
    }

    companion object {
        internal fun drawOverlays(canvas: Canvas, width: Int, height: Int, config: EditorConfig) {
            config.texts.forEach { drawText(canvas, width, height, it) }
            config.stamps.forEach { drawStamp(canvas, width, height, it) }
        }

        internal fun textPaint(width: Float, text: EditorText): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_COLORS[text.colorIndex.mod(TEXT_COLORS.size)]
            textSize = width * 0.075f
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setShadowLayer(max(2f, width * 0.006f), 0f, 2f, Color.BLACK)
        }

        internal fun stampPaint(width: Float, stamp: EditorStamp): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = width * 0.12f * stamp.scale.coerceIn(0.3f, 3f)
            textAlign = Paint.Align.CENTER
            setShadowLayer(max(2f, width * 0.006f), 0f, 2f, Color.BLACK)
        }

        private fun drawText(canvas: Canvas, width: Int, height: Int, text: EditorText) {
            canvas.drawText(text.text, text.xFraction * width, text.yFraction * height, textPaint(width.toFloat(), text))
        }

        private fun drawStamp(canvas: Canvas, width: Int, height: Int, stamp: EditorStamp) {
            val x = stamp.xFraction * width
            val y = stamp.yFraction * height
            val paint = stampPaint(width.toFloat(), stamp)
            canvas.withRotation(stamp.rotation, x, y) {
                drawText(stamp.symbol, x, y, paint)
            }
        }

        const val MAX_SIDE = 1600
        val TEXT_COLORS = intArrayOf(
            Color.WHITE,
            Color.rgb(117, 246, 196),
            Color.rgb(255, 201, 107),
            Color.rgb(255, 120, 145),
        )
        val STAMP_CHOICES = listOf("★", "🔥", "🎮", "😂")
    }
}
