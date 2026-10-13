package com.gamebridge.km

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gamebridge.km.data.KeymapProfile
import com.gamebridge.km.ui.BridgeViewModel
import com.gamebridge.km.ui.screens.EditorScreen
import com.gamebridge.km.ui.screens.HomeScreen
import com.gamebridge.km.ui.screens.SettingsScreen
import com.gamebridge.km.ui.theme.GameBridgeTheme

sealed class Screen {
    data object Home : Screen()
    data class Editor(val profile: KeymapProfile) : Screen()
    data object Settings : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GameBridgeTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot() {
    val vm: BridgeViewModel = viewModel()
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    when (val s = screen) {
        is Screen.Home -> HomeScreen(
            vm = vm,
            onEditProfile = { profile -> screen = Screen.Editor(profile) },
            onOpenSettings = { screen = Screen.Settings }
        )

        is Screen.Editor -> EditorScreen(
            initial = s.profile,
            onSave = { updated ->
                vm.saveProfile(updated)
                screen = Screen.Home
            },
            onBack = { screen = Screen.Home }
        )

        is Screen.Settings -> SettingsScreen(
            vm = vm,
            onBack = { screen = Screen.Home }
        )
    }
}
