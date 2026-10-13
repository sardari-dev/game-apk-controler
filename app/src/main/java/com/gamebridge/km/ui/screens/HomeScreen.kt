package com.gamebridge.km.ui.screens

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.gamebridge.km.R
import com.gamebridge.km.data.KeymapProfile
import com.gamebridge.km.ui.BridgeViewModel

@Composable
fun HomeScreen(
    vm: BridgeViewModel,
    onEditProfile: (KeymapProfile) -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val serviceConnected by vm.serviceConnected.collectAsState()
    val games by vm.games.collectAsState()
    val selectedPkg by vm.selectedPackage.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val activeMap by vm.activeMap.collectAsState()

    var showPicker by remember { mutableStateOf(false) }
    var showProfiles by remember { mutableStateOf(false) }

    val selectedGame = games.firstOrNull { it.pkg == selectedPkg }
    val activeProfile = profiles.firstOrNull { it.id == activeMap[selectedPkg] }
        ?: profiles.firstOrNull()
    val mouseOk = remember { BridgeViewModel.hasMouse(context) }
    val keyboardOk = remember { BridgeViewModel.hasPhysicalKeyboard(context) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(20.dp))

        // ---------- header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                bitmap = remember(context) {
                    context.packageManager.getApplicationIcon(context.packageName)
                        .toBitmap().asImageBitmap()
                },
                contentDescription = null,
                modifier = Modifier.size(46.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.app_name), fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text("KB&M → Touch bridge", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---------- disclaimer gate
        if (!settings.acceptedDisclaimer) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1A0C)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.disclaimer_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.disclaimer_body), fontSize = 13.sp, lineHeight = 19.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = false,
                            onCheckedChange = { checked ->
                                if (checked) vm.updateSettings { it.copy(acceptedDisclaimer = true) }
                            }
                        )
                        Text(stringResource(R.string.disclaimer_accept), fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // ---------- status cards
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusCard(
                icon = { Icon(if (serviceConnected) Icons.Filled.CheckCircle else Icons.Filled.Error, null, tint = if (serviceConnected) Color(0xFF34D399) else MaterialTheme.colorScheme.error) },
                title = stringResource(R.string.status_service),
                value = stringResource(if (serviceConnected) R.string.service_on else R.string.service_off),
                modifier = Modifier.weight(1f),
                onClick = {
                    runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                }
            )
            StatusCard(
                icon = { Icon(Icons.Filled.Mouse, null, tint = if (mouseOk) Color(0xFF34D399) else MaterialTheme.colorScheme.onSurfaceVariant) },
                title = stringResource(R.string.mouse_connected),
                value = stringResource(if (mouseOk) R.string.service_on else R.string.not_connected),
                modifier = Modifier.weight(1f),
                onClick = null
            )
            StatusCard(
                icon = { Icon(Icons.Filled.Keyboard, null, tint = if (keyboardOk) Color(0xFF34D399) else MaterialTheme.colorScheme.onSurfaceVariant) },
                title = stringResource(R.string.keyboard_connected),
                value = stringResource(if (keyboardOk) R.string.service_on else R.string.not_connected),
                modifier = Modifier.weight(1f),
                onClick = null
            )
        }

        Spacer(Modifier.height(20.dp))

        // ---------- game selection
        Text(stringResource(R.string.select_game), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))

        if (selectedGame != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        bitmap = remember(selectedGame.pkg) {
                            runCatching {
                                context.packageManager.getApplicationIcon(selectedGame.pkg).toBitmap()
                            }.getOrDefault(
                                android.graphics.drawable.ColorDrawable(0xFF3F3F46.toInt()).toBitmap(96, 96)
                            ).asImageBitmap()
                        },
                        contentDescription = selectedGame.label,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(selectedGame.label, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(selectedGame.pkg, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    Icon(Icons.Filled.Gamepad, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // quick chips of known games
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            games.filter { it.isKnown }.take(3).forEach { g ->
                FilterChip(
                    selected = g.pkg == selectedPkg,
                    onClick = { vm.selectPackage(g.pkg) },
                    label = { Text(g.label, fontSize = 12.sp, maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.pick_app))
        }

        Spacer(Modifier.height(20.dp))

        // ---------- profile
        Text(stringResource(R.string.profile), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showProfiles = true }
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(activeProfile?.name ?: "—", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        "${activeProfile?.bindings?.size ?: 0} actions",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(stringResource(R.string.edit), color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { activeProfile?.let(onEditProfile) },
                modifier = Modifier.weight(1f),
                enabled = activeProfile != null
            ) {
                Icon(Icons.Filled.Edit, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.edit_keymap))
            }
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings))
            }
        }

        Spacer(Modifier.height(24.dp))

        // ---------- launch
        Button(
            onClick = {
                if (!serviceConnected) {
                    Toast.makeText(context, context.getString(R.string.toast_enable_service_first), Toast.LENGTH_LONG).show()
                } else {
                    vm.launchGame()
                    Toast.makeText(context, context.getString(R.string.toast_launching), Toast.LENGTH_SHORT).show()
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = settings.acceptedDisclaimer && selectedPkg != null
        ) {
            Icon(Icons.Filled.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.launch_game), fontSize = 17.sp, fontWeight = FontWeight.Black)
        }

        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(
                R.string.hint_hotkeys,
                BridgeViewModel.keyLabel(settings.lockKeyCode),
                BridgeViewModel.keyLabel(settings.menuKeyCode)
            ),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Text(
                stringResource(R.string.samsung_tip),
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(14.dp)
            )
        }

        Spacer(Modifier.height(32.dp))
    }

    if (showPicker) {
        GamePickerDialog(
            games = games,
            onSelect = { vm.selectPackage(it); showPicker = false },
            onDismiss = { showPicker = false }
        )
    }

    if (showProfiles) {
        ProfilePickerDialog(
            profiles = profiles,
            selectedId = activeMap[selectedPkg] ?: profiles.firstOrNull()?.id,
            onSelect = {
                selectedPkg?.let { pkg -> vm.setActiveProfile(pkg, it) }
                showProfiles = false
            },
            onDismiss = { showProfiles = false }
        )
    }
}

@Composable
private fun StatusCard(
    icon: @Composable () -> Unit,
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable { onClick() } else Modifier
        )
    ) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            icon()
            Spacer(Modifier.height(6.dp))
            Text(title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GamePickerDialog(
    games: List<com.gamebridge.km.ui.GameAppInfo>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = games.filter { it.label.contains(query, true) || it.pkg.contains(query, true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text(stringResource(R.string.all_apps)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.height(420.dp)) {
                    items(filtered, key = { it.pkg }) { g ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(g.pkg) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(g.label, Modifier.weight(1f), fontSize = 14.sp)
                            if (g.isKnown) Text("★", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun ProfilePickerDialog(
    profiles: List<KeymapProfile>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text(stringResource(R.string.profile)) },
        text = {
            LazyColumn(Modifier.height(300.dp)) {
                items(profiles, key = { it.id }) { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(p.id) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(p.name, Modifier.weight(1f))
                        if (p.id == selectedId) {
                            Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    )
}
