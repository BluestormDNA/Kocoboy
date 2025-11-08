package io.github.bluestormdna.kocoboy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.bluestormdna.kocoboy.core.JoypadInputs
import io.github.bluestormdna.kocoboy.ui.dmg.GameBoy
import io.github.bluestormdna.kocoboy.ui.dmg.KeyDown
import io.github.bluestormdna.kocoboy.ui.dmg.KeyUp
import io.github.bluestormdna.kocoboy.ui.dmg.UnitShape
import io.github.bluestormdna.kocoboy.ui.dmg.horizontalScreenShadow
import io.github.bluestormdna.kocoboy.ui.dmg.verticalScreenShadow
import io.github.bluestormdna.kocoboy.ui.keyboardInputMap
import io.github.bluestormdna.kocoboy.ui.main.MainViewModel
import io.github.bluestormdna.kocoboy.ui.main.SideBar
import io.github.bluestormdna.kocoboy.ui.theme.KocoBoyTheme
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher

@Composable
fun App() {
    val vm = viewModel<MainViewModel>(factory = MainViewModel.factory)

    val colorTheme by vm.colorTheme.collectAsState()
    val frameBuffer by vm.frame.collectAsState()
    // val vps by vm.vps.collectAsState()
    val poweredOn by vm.poweredOn.collectAsState()
    val cartridgeHeader by vm.cartridgeHeader.collectAsState()

    val filePicker = rememberFilePickerLauncher { file ->
        file ?: return@rememberFilePickerLauncher
        vm.loadRom(file)
    }

    KocoBoyTheme(colorTheme) {
        Scaffold(
            modifier = Modifier
                .onKeyEvent { keyEvent: KeyEvent ->
                    val input: JoypadInputs? = keyboardInputMap[keyEvent.key]
                    if (input != null) {
                        when (keyEvent.type) {
                            KeyEventType.KeyDown -> vm.handlePress(input)
                            KeyEventType.KeyUp -> vm.handleRelease(input)
                        }
                    }
                    input != null
                },
            contentWindowInsets = WindowInsets.safeContent,
        ) { contentPadding ->
            Box(
                Modifier.fillMaxSize().padding(contentPadding),
            ) {
                // Text(text = vps.toString())
                var showSettings by rememberSaveable { mutableStateOf(false) }

                Row(
                    modifier = Modifier.matchParentSize(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GameBoy(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(0.9f)
                            .wrapContentSize()
                            .shadow(8.dp, UnitShape),
                        uiJoyPadEvent = { uiJoyPadEvent ->
                            when (uiJoyPadEvent) {
                                is KeyDown -> vm.handlePress(uiJoyPadEvent.key)
                                is KeyUp -> vm.handleRelease(uiJoyPadEvent.key)
                            }
                        },
                        screen = {
                            Canvas(modifier = Modifier.matchParentSize()) {
                                drawImage(
                                    image = frameBuffer,
                                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                                    filterQuality = FilterQuality.Low,
                                )
                                drawRect(horizontalScreenShadow)
                                drawRect(verticalScreenShadow)
                            }
                        },
                        poweredOn = { poweredOn },
                    )
                    AnimatedVisibility(
                        visible = showSettings,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally(),
                    ) {
                        SideBar(
                            cartridgeHeader = cartridgeHeader,
                            onLoadBios = { },
                            onLoadRom = filePicker::launch,
                            onPowerSwitch = vm::powerSwitch,
                            onThemeChange = vm::themeChange,
                        )
                    }
                }

                IconButton(
                    modifier = Modifier.align(Alignment.TopEnd),
                    onClick = { showSettings = !showSettings },
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
        }
    }
}
