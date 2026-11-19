package com.realme.feature.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.realme.data.local.ImageFileStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ImageEditorEngineTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun cropFilterEffectsTextAndSaveProduceAPostableImage() = runBlocking {
        val images = ImageFileStore(context)
        val source = Bitmap.createBitmap(160, 100, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.rgb(30, 90, 150))
        val sourcePath = images.saveBitmap(source, "editor-test")
        source.recycle()
        val engine = ImageEditorEngine(images)
        val loaded = engine.load(sourcePath)
        try {
            val config = EditorConfig(
                filter = EditorFilter.WARM,
                cropRatio = CropRatio.SQUARE,
                effect = EditorEffect.MOSAIC,
                effectRect = NormalizedRect(0.2f, 0.2f, 0.8f, 0.8f),
                texts = listOf(EditorText(1, "TEST", 0.5f, 0.4f, 1)),
                stamps = listOf(EditorStamp(2, "★", 0.7f, 0.7f, scale = 1.5f, rotation = 25f)),
            )
            val base = engine.renderBase(loaded, config)
            try {
                assertEquals(base.width, base.height)
            } finally {
                base.recycle()
            }
            val freeCrop = engine.renderBase(
                loaded,
                EditorConfig(
                    cropRatio = CropRatio.FREE,
                    freeCrop = NormalizedRect(0.1f, 0.2f, 0.9f, 0.8f),
                ),
            )
            try {
                assertEquals(128, freeCrop.width)
                assertEquals(60, freeCrop.height)
            } finally {
                freeCrop.recycle()
            }
            val final = engine.renderFinal(loaded, config)
            val saved = try {
                engine.save(final)
            } finally {
                final.recycle()
            }
            assertTrue(File(saved).isFile)
            assertTrue(File(saved).length() > 0)
            images.delete(saved)
        } finally {
            loaded.recycle()
            images.delete(sourcePath)
        }
    }

    @Test
    fun partialEffectsChangeOnlyTheSelectedRegion() = runBlocking {
        val source = Bitmap.createBitmap(100, 80, Bitmap.Config.ARGB_8888)
        for (y in 0 until source.height) for (x in 0 until source.width) {
            source.setPixel(x, y, if ((x + y) % 2 == 0) Color.RED else Color.BLUE)
        }
        val engine = ImageEditorEngine(ImageFileStore(context))
        try {
            for (effect in listOf(EditorEffect.BLUR, EditorEffect.MOSAIC)) {
                val rendered = engine.renderBase(source, EditorConfig(effect = effect, effectRect = NormalizedRect(0.25f, 0.25f, 0.75f, 0.75f)))
                try {
                    var changedInside = 0
                    for (y in 0 until source.height) for (x in 0 until source.width) {
                        val changed = source.getPixel(x, y) != rendered.getPixel(x, y)
                        if (x in 25 until 75 && y in 20 until 60) {
                            if (changed) changedInside++
                        } else {
                            assertTrue("$effect changed a pixel outside the selected region ($x,$y)", !changed)
                        }
                    }
                    assertTrue("$effect did not change the selected region", changedInside > 0)
                } finally { rendered.recycle() }
            }
        } finally { source.recycle() }
    }

    @Test
    fun effectsOnSinglePixelProduceUsableBitmap() = runBlocking {
        val source = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.RED)
        val engine = ImageEditorEngine(ImageFileStore(context))
        try {
            for (effect in listOf(EditorEffect.BLUR, EditorEffect.MOSAIC)) {
                val result = engine.renderFinal(source, EditorConfig(effect = effect))
                try {
                    assertEquals(1, result.width)
                    assertEquals(Color.RED, result.getPixel(0, 0))
                } finally { result.recycle() }
            }
        } finally { source.recycle() }
    }
}
