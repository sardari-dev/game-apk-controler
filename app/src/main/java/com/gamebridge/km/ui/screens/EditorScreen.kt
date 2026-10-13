package com.gamebridge.km.ui.screens

import android.content.Context
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.gamebridge.km.R
import com.gamebridge.km.data.ActionType
import com.gamebridge.km.data.KeyBinding
import com.gamebridge.km.data.KeymapProfile
import com.gamebridge.km.data.Trigger
import com.gamebridge.km.data.TriggerKind
import com.gamebridge.km.data.triggerDisplay
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    initial: KeymapProfile,
    onSave: (KeymapProfile) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var work by remember { mutableStateOf(initial) }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val positions = remember { mutableStateMapOf<String, Offset>() }
    var joyCenter by remember { mutableStateOf(Offset.Zero) }
    var joyRadius by remember { mutableStateOf(0f) }
    var anchor by remember { mutableStateOf(Offset.Zero) }

    var selectedId by remember { mutableStateOf<String?>(null) }
    var captureMode by remember { mutableStateOf(false) }
    var screenshotUri by remember { mutableStateOf<Uri?>(null) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        screenshotUri = uri
    }

    // initialize pixel positions from fractions once the canvas is measured
    LaunchedEffect(canvasSize) {
        if (canvasSize == IntSize.Zero) return@LaunchedEffect
        if (joyRadius == 0f) {
            joyCenter = Offset(
                work.joystick.centerX * canvasSize.width,
                work.joystick.centerY * canvasSize.height
            )
            joyRadius = work.joystick.radiusFrac * minOf(canvasSize.width, canvasSize.height)
            anchor = Offset(
                work.camera.anchorX * canvasSize.width,
                work.camera.anchorY * canvasSize.height
            )
        }
        work.bindings.forEach { b ->
            if (b.id !in positions) {
                positions[b.id] = Offset(b.x * canvasSize.width, b.y * canvasSize.height)
            }
        }
    }

    fun buildOut(): KeymapProfile {
        val w = canvasSize.width.coerceAtLeast(1)
        val h = canvasSize.height.coerceAtLeast(1)
        val minDim = minOf(w, h).toFloat()
        return work.copy(
            joystick = work.joystick.copy(
                centerX = (joyCenter.x / w).coerceIn(0.02f, 0.98f),
                centerY = (joyCenter.y / h).coerceIn(0.02f, 0.98f),
                radiusFrac = (joyRadius / minDim).coerceIn(0.05f, 0.20f)
            ),
            camera = work.camera.copy(
                anchorX = (anchor.x / w).coerceIn(0.02f, 0.98f),
                anchorY = (anchor.y / h).coerceIn(0.02f, 0.98f)
            ),
            bindings = work.bindings.map { b ->
                positions[b.id]?.let { off ->
                    b.copy(
                        x = (off.x / w).coerceIn(0.02f, 0.98f),
                        y = (off.y / h).coerceIn(0.02f, 0.98f)
                    )
                } ?: b
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.editor_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Icon(Icons.Filled.Image, contentDescription = stringResource(R.string.pick_screenshot))
                    }
                    IconButton(onClick = { onSave(buildOut()) }) {
                        Icon(Icons.Filled.Save, contentDescription = stringResource(R.string.save))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                val aspect = remember { screenAspect(context) }
                Box(
                    Modifier
                        .fillMaxHeight()
                        .aspectRatio(aspect)
                        .background(Color(0xFF101014), RoundedCornerShape(14.dp))
                        .onSizeChanged { canvasSize = it }
                ) {
                    if (screenshotUri != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(screenshotUri).crossfade(false).build(),
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        GridBackground(Modifier.fillMaxSize())
                    }

                    JoystickWidget(
                        center = joyCenter,
                        radius = joyRadius,
                        onCenterChanged = { joyCenter = it },
                        onRadiusChanged = { joyRadius = it },
                        enabled = canvasSize != IntSize.Zero
                    )

                    CameraAnchorWidget(pos = anchor, onPosChanged = { anchor = it })

                    work.bindings.forEach { b ->
                        val pos = positions[b.id] ?: Offset.Zero
                        BindingChip(
                            binding = b,
                            pos = pos,
                            selected = b.id == selectedId,
                            onTap = { selectedId = b.id },
                            onPosChanged = { positions[b.id] = it }
                        )
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.screenshot_hint),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = {
                    val id = "b-custom-${System.currentTimeMillis()}"
                    val nb = KeyBinding(
                        id = id,
                        label = "New",
                        trigger = Trigger(TriggerKind.KEY, android.view.KeyEvent.KEYCODE_UNKNOWN),
                        x = 0.5f, y = 0.35f
                    )
                    work = work.copy(bindings = work.bindings + nb)
                    positions[id] = Offset(canvasSize.width * 0.5f, canvasSize.height * 0.35f)
                    selectedId = id
                }) {
                    Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.add_button))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    val selected = work.bindings.firstOrNull { it.id == selectedId }
    if (selected != null) {
        ModalBottomSheet(onDismissRequest = { selectedId = null }) {
            BindingEditorContent(
                binding = selected,
                onChange = { nb ->
                    work = work.copy(bindings = work.bindings.map { if (it.id == nb.id) nb else it })
                },
                onCaptureKey = { captureMode = true },
                onDelete = {
                    work = work.copy(bindings = work.bindings.filter { it.id != selected.id })
                    positions.remove(selected.id)
                    selectedId = null
                }
            )
        }
    }

    if (captureMode && selected != null) {
        KeyCaptureDialog(
            onKey = { keyCode ->
                work = work.copy(
                    bindings = work.bindings.map {
                        if (it.id == selected.id) it.copy(trigger = Trigger(TriggerKind.KEY, keyCode)) else it
                    }
                )
                captureMode = false
            },
            onDismiss = { captureMode = false }
        )
    }
}

// ---------------------------------------------------------------- widgets

private fun Offset.coerceInBox(w: Float, h: Float): Offset =
    Offset(x.coerceIn(0f, w), y.coerceIn(0f, h))

@Composable
private fun GridBackground(modifier: Modifier) {
    Canvas(modifier) {
        val step = 46.dp.toPx()
        val c = Color(0xFF1C1C24)
        var x = 0f
        while (x < size.width) {
            drawLine(c, Offset(x, 0f), Offset(x, size.height), 1f)
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(c, Offset(0f, y), Offset(size.width, y), 1f)
            y += step
        }
    }
}

@Composable
private fun JoystickWidget(
    center: Offset,
    radius: Float,
    onCenterChanged: (Offset) -> Unit,
    onRadiusChanged: (Float) -> Unit,
    enabled: Boolean
) {
    val curCenter by rememberUpdatedState(center)
    val curRadius by rememberUpdatedState(radius)
    val curOnCenter by rememberUpdatedState(onCenterChanged)
    val curOnRadius by rememberUpdatedState(onRadiusChanged)

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                var mode = 0 // 1 = move center, 2 = radius handle
                detectDragGestures(
                    onDragStart = { off ->
                        val handle = Offset(
                            curCenter.x + curRadius * 0.7071f,
                            curCenter.y + curRadius * 0.7071f
                        )
                        mode = when {
                            (off - handle).getDistance() < 48.dp.toPx() -> 2
                            (off - curCenter).getDistance() < curRadius * 1.15f + 24.dp.toPx() -> 1
                            else -> 0
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        when (mode) {
                            1 -> curOnCenter(
                                change.position.coerceInBox(size.width.toFloat(), size.height.toFloat())
                            )
                            2 -> curOnRadius(
                                (change.position - curCenter).getDistance()
                                    .coerceIn(40.dp.toPx(), size.width * 0.35f)
                            )
                        }
                    }
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color(0x33F59E0B), radius, center)
            drawCircle(Color(0xFFF59E0B), radius, center, style = Stroke(3f))
            drawCircle(Color(0xFFF59E0B), 10f, center)
            val hx = center.x + radius * 0.7071f
            val hy = center.y + radius * 0.7071f
            drawCircle(Color(0xFFF59E0B), 14f, Offset(hx, hy))
        }
        Text(
            "WASD",
            color = Color.White,
            fontSize = 11.sp,
            modifier = Modifier.offset {
                IntOffset(center.x.roundToInt() - 22, center.y.roundToInt() + 14)
            }
        )
    }
}

@Composable
private fun CameraAnchorWidget(pos: Offset, onPosChanged: (Offset) -> Unit) {
    val curPos by rememberUpdatedState(pos)
    val curOnPos by rememberUpdatedState(onPosChanged)

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var drag = false
                detectDragGestures(
                    onDragStart = { off ->
                        drag = (off - curPos).getDistance() < 64.dp.toPx()
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        if (drag) {
                            curOnPos(
                                (curPos + amount).coerceInBox(
                                    size.width.toFloat(),
                                    size.height.toFloat()
                                )
                            )
                        }
                    }
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color(0xFF34D399), 12f, pos, style = Stroke(3f))
            drawLine(Color(0xFF34D399), pos - Offset(26f, 0f), pos + Offset(26f, 0f), 3f)
            drawLine(Color(0xFF34D399), pos - Offset(0f, 26f), pos + Offset(0f, 26f), 3f)
        }
        Text(
            "CAM",
            color = Color(0xFF34D399),
            fontSize = 10.sp,
            modifier = Modifier.offset {
                IntOffset(pos.x.roundToInt() - 18, pos.y.roundToInt() + 18)
            }
        )
    }
}

@Composable
private fun BindingChip(
    binding: KeyBinding,
    pos: Offset,
    selected: Boolean,
    onTap: () -> Unit,
    onPosChanged: (Offset) -> Unit
) {
    var chipSize by remember { mutableStateOf(IntSize.Zero) }
    val curPos by rememberUpdatedState(pos)
    val curOnPos by rememberUpdatedState(onPosChanged)

    Box(
        Modifier
            .offset {
                IntOffset(
                    curPos.x.roundToInt() - chipSize.width / 2,
                    curPos.y.roundToInt() - chipSize.height / 2
                )
            }
            .onSizeChanged { chipSize = it }
            .background(
                if (selected) Color(0xE6F59E0B) else Color(0xCC1F1F27),
                RoundedCornerShape(12.dp)
            )
            .border(1.dp, if (selected) Color.White else Color(0xFFF59E0B), RoundedCornerShape(12.dp))
            .clickable { onTap() }
            .pointerInput(binding.id) {
                detectDragGestures { change, amount ->
                    change.consume()
                    curOnPos(
                        (curPos + amount).coerceInBox(size.width.toFloat(), size.height.toFloat())
                    )
                }
            }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            "${binding.label} · ${triggerDisplay(binding.trigger)}",
            color = if (selected) Color(0xFF1A1206) else Color.White,
            fontSize = 12.sp
        )
    }
}

// ---------------------------------------------------------------- sheet

@Composable
private fun BindingEditorContent(
    binding: KeyBinding,
    onChange: (KeyBinding) -> Unit,
    onCaptureKey: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(binding.label, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = binding.label,
            onValueChange = { onChange(binding.copy(label = it)) },
            label = { Text(stringResource(R.string.button_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.trigger), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                triggerDisplay(binding.trigger),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary
            )
            OutlinedButton(onClick = onCaptureKey) {
                Text(stringResource(R.string.record_key))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = binding.trigger.kind == TriggerKind.MOUSE_PRIMARY,
                onClick = { onChange(binding.copy(trigger = Trigger(TriggerKind.MOUSE_PRIMARY))) },
                label = { Text(stringResource(R.string.mouse_left), fontSize = 11.sp) }
            )
            FilterChip(
                selected = binding.trigger.kind == TriggerKind.MOUSE_SECONDARY,
                onClick = { onChange(binding.copy(trigger = Trigger(TriggerKind.MOUSE_SECONDARY))) },
                label = { Text(stringResource(R.string.mouse_right), fontSize = 11.sp) }
            )
            FilterChip(
                selected = binding.trigger.kind == TriggerKind.MOUSE_TERTIARY,
                onClick = { onChange(binding.copy(trigger = Trigger(TriggerKind.MOUSE_TERTIARY))) },
                label = { Text(stringResource(R.string.mouse_middle), fontSize = 11.sp) }
            )
            FilterChip(
                selected = binding.trigger.kind == TriggerKind.WHEEL_UP,
                onClick = { onChange(binding.copy(trigger = Trigger(TriggerKind.WHEEL_UP))) },
                label = { Text(stringResource(R.string.wheel_up), fontSize = 11.sp) }
            )
            FilterChip(
                selected = binding.trigger.kind == TriggerKind.WHEEL_DOWN,
                onClick = { onChange(binding.copy(trigger = Trigger(TriggerKind.WHEEL_DOWN))) },
                label = { Text(stringResource(R.string.wheel_down), fontSize = 11.sp) }
            )
        }

        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.action_type), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = binding.type == ActionType.TAP,
                onClick = { onChange(binding.copy(type = ActionType.TAP)) },
                label = { Text(stringResource(R.string.action_tap), fontSize = 11.sp) }
            )
            FilterChip(
                selected = binding.type == ActionType.HOLD,
                onClick = { onChange(binding.copy(type = ActionType.HOLD)) },
                label = { Text(stringResource(R.string.action_hold), fontSize = 11.sp) }
            )
            FilterChip(
                selected = binding.type == ActionType.TOGGLE,
                onClick = { onChange(binding.copy(type = ActionType.TOGGLE)) },
                label = { Text(stringResource(R.string.action_toggle), fontSize = 11.sp) }
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.is_aim), fontSize = 13.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = binding.isAim,
                onCheckedChange = { onChange(binding.copy(isAim = it)) }
            )
        }

        Spacer(Modifier.height(18.dp))
        TextButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(28.dp))
    }
}

// ------------------------------------------------------------- key capture

@Composable
fun KeyCaptureDialog(onKey: (Int) -> Unit, onDismiss: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(
                Modifier.padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.record_key), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Press any key on the keyboard",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(88.dp)
                        .background(Color(0xFF101014), RoundedCornerShape(12.dp))
                        .focusRequester(focusRequester)
                        .focusable()
                        .onPreviewKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown) {
                                onKey(e.key.nativeKeyCode)
                                true
                            } else false
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("⌨", fontSize = 30.sp)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- helpers

private fun screenAspect(context: Context): Float {
    return runCatching {
        val wm = context.getSystemService(WindowManager::class.java)
        if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.currentWindowMetrics.bounds
            b.width().toFloat() / b.height().toFloat()
        } else {
            @Suppress("DEPRECATION")
            val dm = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            dm.widthPixels.toFloat() / dm.heightPixels.toFloat()
        }
    }.getOrDefault(0.45f)
}
