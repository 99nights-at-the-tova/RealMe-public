package com.realme.feature.editor

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Canvas
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import com.realme.core.design.RealMeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ImageEditorScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun previewAndFilterControlsAreInteractive() {
        val bitmap = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(40, 90, 130))
        var selectedFilter = EditorFilter.ORIGINAL
        try {
            composeRule.setContent {
                RealMeTheme {
                    ImageEditorScreen(
                        state = ImageEditorUiState(
                            isLoading = false,
                            basePreview = bitmap,
                            config = EditorConfig(),
                        ),
                        onBack = {},
                        onFilter = { selectedFilter = it },
                        onCrop = {},
                        onFreeCrop = {},
                        onEffect = {},
                        onEffectRect = {},
                        onUndo = {},
                        onAddText = { _, _, _ -> },
                        onCycleTextColor = {},
                        onMoveText = { _, _, _ -> },
                        onAddStamp = { _, _, _ -> },
                        onMoveStamp = { _, _, _ -> },
                        onScaleStamp = {},
                        onRotateStamp = {},
                        onSave = {},
                        onSaved = {},
                        onClearError = {},
                    )
                }
            }
            composeRule.onNodeWithTag("editor_preview").assertIsDisplayed()
            composeRule.onNodeWithText("モノクロ").performScrollTo().performClick()
            composeRule.runOnIdle { assertEquals(EditorFilter.MONO, selectedFilter) }
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun transformedStampPreviewMatchesSavedImageGeometry() {
        val bitmap = Bitmap.createBitmap(160, 100, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val config = EditorConfig(stamps = listOf(EditorStamp(1, "★", 0.65f, 0.65f, scale = 1.5f, rotation = 45f)))
        try {
            composeRule.setContent {
                RealMeTheme {
                    ImageEditorScreen(
                        state = ImageEditorUiState(isLoading = false, basePreview = bitmap, config = config),
                        onBack = {}, onFilter = {}, onCrop = {}, onFreeCrop = {}, onEffect = {},
                        onEffectRect = {}, onUndo = {}, onAddText = { _, _, _ -> }, onCycleTextColor = {},
                        onMoveText = { _, _, _ -> }, onAddStamp = { _, _, _ -> }, onMoveStamp = { _, _, _ -> },
                        onScaleStamp = {}, onRotateStamp = {}, onSave = {}, onSaved = {}, onClearError = {},
                    )
                }
            }
            val actual = composeRule.onNodeWithTag("editor_preview").captureToImage().asAndroidBitmap()
            val expected = Bitmap.createBitmap(actual.width, actual.height, Bitmap.Config.ARGB_8888)
            try {
                expected.eraseColor(Color.WHITE)
                ImageEditorEngine.drawOverlays(Canvas(expected), expected.width, expected.height, config)
                val a = darkBounds(actual)
                val e = darkBounds(expected)
                for (i in a.indices) {
                    assertTrue("Preview stamp bounds ${a.toList()} differ from saved geometry ${e.toList()}", kotlin.math.abs(a[i] - e[i]) <= 8)
                }
            } finally { expected.recycle() }
        } finally { bitmap.recycle() }
    }

    @Test
    fun freeCropSliderAppliesItsValueWithoutExtraButton() {
        val bitmap = Bitmap.createBitmap(160, 100, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        var selected: NormalizedRect? = null
        try {
            composeRule.setContent {
                RealMeTheme {
                    ImageEditorScreen(
                        state = ImageEditorUiState(isLoading = false, basePreview = bitmap, config = EditorConfig(cropRatio = CropRatio.FREE)),
                        onBack = {}, onFilter = {}, onCrop = {}, onFreeCrop = { selected = it }, onEffect = {},
                        onEffectRect = {}, onUndo = {}, onAddText = { _, _, _ -> }, onCycleTextColor = {},
                        onMoveText = { _, _, _ -> }, onAddStamp = { _, _, _ -> }, onMoveStamp = { _, _, _ -> },
                        onScaleStamp = {}, onRotateStamp = {}, onSave = {}, onSaved = {}, onClearError = {},
                    )
                }
            }
            composeRule.onNodeWithContentDescription("自由クロップ範囲 左")
                .performScrollTo()
                .performSemanticsAction(SemanticsActions.SetProgress) { it(0.3f) }
            composeRule.runOnIdle { assertEquals(0.3f, requireNotNull(selected).left, 0.001f) }
        } finally { bitmap.recycle() }
    }

    private fun darkBounds(bitmap: Bitmap): IntArray {
        var left = bitmap.width
        var top = bitmap.height
        var right = -1
        var bottom = -1
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            if (Color.red(pixel) < 100 && Color.green(pixel) < 100 && Color.blue(pixel) < 100) {
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x)
                bottom = maxOf(bottom, y)
            }
        }
        assertTrue("Stamp was not drawn", right >= left && bottom >= top)
        return intArrayOf(left, top, right, bottom)
    }
}
