package io.github.bluestormdna.kocoboy.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.bluestormdna.kocoboy.core.cartridge.EmptyCartridgeHeader
import io.github.bluestormdna.kocoboy.ui.main.EmulatorSettings

@Preview
@Composable
fun EmulatorSettingsPreview() {
    EmulatorSettings(
        cartridgeHeader = EmptyCartridgeHeader(),
        onLoadBios = {},
        onLoadRom = {},
        onPowerSwitch = {},
    )
}