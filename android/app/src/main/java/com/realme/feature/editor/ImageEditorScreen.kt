package com.realme.feature.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.realme.core.design.InlineError
import com.realme.core.model.JST
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.sin
import kotlin.math.cos

private enum class EditorTool { NONE, TEXT, STAMP }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageEditorScreen(
    state: ImageEditorUiState,
    onBack: () -> Unit,
    onFilter: (EditorFilter) -> Unit,
    onCrop: (CropRatio) -> Unit,
    onFreeCrop: (NormalizedRect) -> Unit,
    onEffect: (EditorEffect) -> Unit,
    onEffectRect: (NormalizedRect) -> Unit,
    onUndo: () -> Unit,
    onAddText: (String, Float, Float) -> Unit,
    onCycleTextColor: () -> Unit,
    onMoveText: (Long, Float, Float) -> Unit,
    onAddStamp: (String, Float, Float) -> Unit,
    onMoveStamp: (Long, Float, Float) -> Unit,
    onScaleStamp: (Float) -> Unit,
    onRotateStamp: (Float) -> Unit,
    onSave: () -> Unit,
    onSaved: (String) -> Unit,
    onClearError: () -> Unit,
) {
    var tool by rememberSaveable { mutableStateOf(EditorTool.NONE) }
    var selectedStamp by rememberSaveable { mutableStateOf(ImageEditorEngine.STAMP_CHOICES.first()) }
    var pendingTextPosition by remember { mutableStateOf<Offset?>(null) }
    var textInput by rememberSaveable { mutableStateOf("") }
    val dragOffsets = remember { mutableStateMapOf<Long, Offset>() }
    LaunchedEffect(state.savedPath) {
        state.savedPath?.let(onSaved)
    }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("画像編集") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    IconButton(onClick = onUndo, enabled = state.canUndo && !state.isBusy) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "元に戻す")
                    }
                    TextButton(onClick = onSave, enabled = !state.isLoading && !state.isBusy) {
                        Text("保存")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.isLoading) {
                Box(Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val preview = state.basePreview
                if (preview != null) {
                    BoxWithConstraints(
                        Modifier.fillMaxWidth()
                            .aspectRatio(preview.width.toFloat() / preview.height.toFloat())
                            .testTag("editor_preview"),
                    ) {
                        val widthPx = constraints.maxWidth.toFloat()
                        val heightPx = constraints.maxHeight.toFloat()
                        Image(
                            bitmap = preview.asImageBitmap(),
                            contentDescription = "編集プレビュー",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                        val displayConfig = state.config.copy(
                            texts = state.config.texts.map { text ->
                                val drag = dragOffsets[text.id] ?: Offset.Zero
                                text.copy(xFraction = text.xFraction + drag.x / widthPx, yFraction = text.yFraction + drag.y / heightPx)
                            },
                            stamps = state.config.stamps.map { stamp ->
                                val drag = dragOffsets[stamp.id] ?: Offset.Zero
                                stamp.copy(xFraction = stamp.xFraction + drag.x / widthPx, yFraction = stamp.yFraction + drag.y / heightPx)
                            },
                        )
                        Canvas(Modifier.fillMaxSize()) {
                            drawIntoCanvas { canvas ->
                                ImageEditorEngine.drawOverlays(canvas.nativeCanvas, size.width.roundToInt(), size.height.roundToInt(), displayConfig)
                            }
                        }
                        Box(
                            Modifier.fillMaxSize().pointerInput(tool, selectedStamp, state.isBusy) {
                                detectTapGestures { point ->
                                    if (state.isBusy) return@detectTapGestures
                                    val x = point.x / widthPx
                                    val y = point.y / heightPx
                                    when (tool) {
                                        EditorTool.TEXT -> pendingTextPosition = Offset(x, y)
                                        EditorTool.STAMP -> onAddStamp(selectedStamp, x, y)
                                        EditorTool.NONE -> Unit
                                    }
                                }
                            },
                        )
                        state.config.stamps.forEach { stamp ->
                            PositionedMark(
                                id = stamp.id,
                                xFraction = stamp.xFraction,
                                yFraction = stamp.yFraction,
                                widthPx = widthPx,
                                heightPx = heightPx,
                                paint = ImageEditorEngine.stampPaint(widthPx, stamp),
                                label = stamp.symbol,
                                rotation = stamp.rotation,
                                enabled = !state.isBusy,
                                dragOffsets = dragOffsets,
                                onMove = { x, y -> onMoveStamp(stamp.id, x, y) },
                            )
                        }
                        state.config.texts.forEach { text ->
                            PositionedMark(
                                id = text.id,
                                xFraction = text.xFraction,
                                yFraction = text.yFraction,
                                widthPx = widthPx,
                                heightPx = heightPx,
                                paint = ImageEditorEngine.textPaint(widthPx, text),
                                label = text.text,
                                enabled = !state.isBusy,
                                dragOffsets = dragOffsets,
                                onMove = { x, y -> onMoveText(text.id, x, y) },
                            )
                        }
                    }
                    Text(
                        "テキスト・スタンプはドラッグで移動できます。スタンプの拡大・回転も反映して保存します。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.error?.let {
                InlineError(it)
                TextButton(onClick = onClearError) { Text("閉じる") }
            }
            state.autoPublishAt?.let {
                Text(
                    "自動投稿予定: ${DateTimeFormatter.ofPattern("HH:mm:ss").withZone(JST).format(it)}",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            ToolSection("フィルター") {
                EditorFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.config.filter == filter,
                        onClick = { onFilter(filter) },
                        label = { Text(filter.label) },
                        enabled = !state.isBusy,
                    )
                }
            }
            ToolSection("切り抜き") {
                CropRatio.entries.forEach { ratio ->
                    FilterChip(
                        selected = state.config.cropRatio == ratio,
                        onClick = { onCrop(ratio) },
                        label = { Text(ratio.label) },
                        enabled = !state.isBusy,
                    )
                }
            }
            if (state.config.cropRatio == CropRatio.FREE) {
                RectSliderControls(
                    title = "自由クロップ範囲",
                    rect = state.config.freeCrop,
                    enabled = !state.isBusy,
                    onChange = onFreeCrop,
                )
            }
            ToolSection("隠す") {
                EditorEffect.entries.forEach { effect ->
                    FilterChip(
                        selected = state.config.effect == effect,
                        onClick = { onEffect(effect) },
                        label = { Text(effect.label) },
                        enabled = !state.isBusy,
                    )
                }
                TextButton(onClick = onCycleTextColor, enabled = state.config.texts.isNotEmpty()) {
                    Text("テキスト色変更")
                }
            }
            if (state.config.effect != EditorEffect.NONE) {
                RectSliderControls(
                    title = "効果をかける範囲",
                    rect = state.config.effectRect,
                    enabled = !state.isBusy,
                    onChange = onEffectRect,
                )
            }
            ToolSection("飾り") {
                FilterChip(
                    selected = tool == EditorTool.TEXT,
                    onClick = { tool = if (tool == EditorTool.TEXT) EditorTool.NONE else EditorTool.TEXT },
                    label = { Text("テキスト") },
                )
                FilterChip(
                    selected = tool == EditorTool.STAMP,
                    onClick = { tool = if (tool == EditorTool.STAMP) EditorTool.NONE else EditorTool.STAMP },
                    label = { Text("スタンプ") },
                )
                if (tool == EditorTool.STAMP) {
                    ImageEditorEngine.STAMP_CHOICES.forEach { symbol ->
                        FilterChip(
                            selected = selectedStamp == symbol,
                            onClick = { selectedStamp = symbol },
                            label = { Text(symbol) },
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(
                    onClick = { onScaleStamp(1.2f) },
                    enabled = state.config.stamps.isNotEmpty() && !state.isBusy,
                ) { Text("拡大") }
                TextButton(
                    onClick = { onScaleStamp(1f / 1.2f) },
                    enabled = state.config.stamps.isNotEmpty() && !state.isBusy,
                ) { Text("縮小") }
                TextButton(
                    onClick = { onRotateStamp(15f) },
                    enabled = state.config.stamps.isNotEmpty() && !state.isBusy,
                ) { Text("15°回転") }
            }
            if (tool != EditorTool.NONE) {
                Text(
                    if (tool == EditorTool.TEXT) "画像をタップしてテキストを追加" else "画像をタップしてスタンプを追加",
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onSave,
                enabled = !state.isLoading && !state.isBusy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (state.isBusy) CircularProgressIndicator(Modifier.size(22.dp)) else Text("編集結果を保存")
            }
        }
    }
    if (pendingTextPosition != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                pendingTextPosition = null
                textInput = ""
            },
            title = { Text("テキストを追加") },
            text = {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it.take(80) },
                    label = { Text("テキスト") },
                    singleLine = false,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingTextPosition?.let { onAddText(textInput, it.x, it.y) }
                        pendingTextPosition = null
                        textInput = ""
                    },
                    enabled = textInput.isNotBlank(),
                ) { Text("追加") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingTextPosition = null
                    textInput = ""
                }) { Text("キャンセル") }
            },
        )
    }
}

@Composable
private fun ToolSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun RectSliderControls(
    title: String,
    rect: NormalizedRect,
    enabled: Boolean,
    onChange: (NormalizedRect) -> Unit,
) {
    var draft by remember(rect) { mutableStateOf(rect) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        RectEdgeSlider(title, "左", draft.left, enabled, 0f..(draft.right - 0.05f).coerceAtLeast(0.001f), { onChange(draft) }) { draft = draft.copy(left = it) }
        RectEdgeSlider(title, "上", draft.top, enabled, 0f..(draft.bottom - 0.05f).coerceAtLeast(0.001f), { onChange(draft) }) { draft = draft.copy(top = it) }
        RectEdgeSlider(title, "右", draft.right, enabled, (draft.left + 0.05f).coerceAtMost(0.999f)..1f, { onChange(draft) }) { draft = draft.copy(right = it) }
        RectEdgeSlider(title, "下", draft.bottom, enabled, (draft.top + 0.05f).coerceAtMost(0.999f)..1f, { onChange(draft) }) { draft = draft.copy(bottom = it) }
        Text("スライダーを離すとプレビューに反映されます。", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun RectEdgeSlider(
    title: String,
    label: String,
    value: Float,
    enabled: Boolean,
    range: ClosedFloatingPointRange<Float>,
    onFinished: () -> Unit,
    onValueChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "$label ${(value * 100).roundToInt()}%",
            modifier = Modifier.width(78.dp),
            style = MaterialTheme.typography.labelSmall,
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onFinished,
            enabled = enabled,
            valueRange = range,
            modifier = Modifier.weight(1f).semantics { contentDescription = "$title $label" },
        )
    }
}

@Composable
private fun PositionedMark(
    id: Long,
    xFraction: Float,
    yFraction: Float,
    widthPx: Float,
    heightPx: Float,
    paint: android.graphics.Paint,
    label: String,
    rotation: Float = 0f,
    enabled: Boolean,
    dragOffsets: MutableMap<Long, Offset>,
    onMove: (Float, Float) -> Unit,
) {
    val density = LocalDensity.current
    val minimum = with(density) { 48.dp.toPx() }
    val glyphWidth = paint.measureText(label)
    val glyphHeight = paint.descent() - paint.ascent()
    val rotated = rotation % 180f != 0f
    val diagonal = sqrt(glyphWidth * glyphWidth + glyphHeight * glyphHeight)
    val targetWidth = max(minimum, if (rotated) diagonal else glyphWidth)
    val targetHeight = max(minimum, if (rotated) diagonal else glyphHeight)
    val centerY = (paint.ascent() + paint.descent()) / 2f
    val radians = Math.toRadians(rotation.toDouble())
    val centerXOffset = (-sin(radians) * centerY).toFloat()
    val centerYOffset = (cos(radians) * centerY).toFloat()
    val drag = dragOffsets[id] ?: Offset.Zero
    Box(
        Modifier
            .offset {
                IntOffset(
                    (xFraction * widthPx + drag.x + centerXOffset - targetWidth / 2f).roundToInt(),
                    (yFraction * heightPx + drag.y + centerYOffset - targetHeight / 2f).roundToInt(),
                )
            }
            .size(with(density) { targetWidth.toDp() }, with(density) { targetHeight.toDp() })
            .semantics { contentDescription = label }
            .pointerInput(id, xFraction, yFraction, enabled) {
                detectDragGestures(
                    onDragEnd = {
                        val delta = dragOffsets.remove(id) ?: Offset.Zero
                        if (enabled && delta != Offset.Zero) {
                            onMove((xFraction * widthPx + delta.x) / widthPx, (yFraction * heightPx + delta.y) / heightPx)
                        }
                    },
                    onDragCancel = { dragOffsets.remove(id) },
                ) { change, amount ->
                    if (enabled) {
                        change.consume()
                        dragOffsets[id] = (dragOffsets[id] ?: Offset.Zero) + amount
                    }
                }
            },
    )
}
