package com.gamebridge.km.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamebridge.km.R
import com.gamebridge.km.data.MouseMode
import com.gamebridge.km.ui.BridgeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: BridgeViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val diagnostics by vm.diagnostics.collectAsState()
    var captureTarget by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            SettingSlider(
                title = stringResource(R.string.sens_x),
                value = settings.sensX,
                onCommit = { v -> vm.updateSettings { it.copy(sensX = v) } }
            )
            SettingSlider(
                title = stringResource(R.string.sens_y),
                value = settings.sensY,
                onCommit = { v -> vm.updateSettings { it.copy(sensY = v) } }
            )
            SettingSlider(
                title = stringResource(R.string.ads_scale),
                value = settings.adsScale,
                range = 0.1f..1.5f,
                onCommit = { v -> vm.updateSettings { it.copy(adsScale = v) } }
            )

            SettingSwitch(
                title = stringResource(R.string.invert_y),
                checked = settings.invertY,
                onChange = { v -> vm.updateSettings { it.copy(invertY = v) } }
            )
            SettingSwitch(
                title = stringResource(R.string.auto_lock),
                checked = settings.autoLock,
                onChange = { v -> vm.updateSettings { it.copy(autoLock = v) } }
            )

            // lock / menu keys
            KeyRow(
                title = stringResource(R.string.lock_key),
                keyLabel = BridgeViewModel.keyLabel(settings.lockKeyCode),
                onCapture = { captureTarget = "lock" }
            )
            KeyRow(
                title = stringResource(R.string.menu_key),
                keyLabel = BridgeViewModel.keyLabel(settings.menuKeyCode),
                onCapture = { captureTarget = "menu" }
            )

            // mouse mode
            Text(stringResource(R.string.mouse_mode), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MouseMode.values().forEach { mode ->
                    FilterChip(
                        selected = settings.mouseMode == mode,
                        onClick = { vm.updateSettings { it.copy(mouseMode = mode) } },
                        label = {
                            Text(
                                when (mode) {
                                    MouseMode.AUTO -> stringResource(R.string.mode_auto)
                                    MouseMode.CAPTURE -> stringResource(R.string.mode_capture)
                                    MouseMode.HOVER -> stringResource(R.string.mode_hover)
                                },
                                fontSize = 11.sp
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // diagnostics
            Text(stringResource(R.string.diagnostics), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.diagnostics_hint),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0D)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    LazyColumn(
                        Modifier
                            .heightIn(max = 260.dp)
                            .padding(10.dp)
                    ) {
                        items(diagnostics.asReversed()) { line ->
                            Text(
                                line,
                                fontSize = 10.sp,
                                lineHeight = 13.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                    TextButton(
                        onClick = { com.gamebridge.km.bridge.Diagnostics.clear() },
                        modifier = Modifier.align(Alignment.End)
                    ) { Text(stringResource(R.string.clear)) }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.about_version, "1.0.0"),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.about_github) + " · github.com/sardari-dev/game-apk-controler",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    if (captureTarget != null) {
        KeyCaptureDialog(
            onKey = { keyCode ->
                val target = captureTarget
                if (target == "lock") vm.updateSettings { it.copy(lockKeyCode = keyCode) }
                if (target == "menu") vm.updateSettings { it.copy(menuKeyCode = keyCode) }
                captureTarget = null
            },
            onDismiss = { captureTarget = null }
        )
    }
}

@Composable
private fun SettingSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float> = 0.1f..5f,
    onCommit: (Float) -> Unit
) {
    var local by remember(value) { mutableStateOf(value) }
    Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onCommit(local) },
            valueRange = range,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(String.format("%.2f", local), fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun SettingSwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 15.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun KeyRow(title: String, keyLabel: String, onCapture: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 15.sp)
        TextButton(onClick = onCapture) {
            Text(keyLabel, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}
