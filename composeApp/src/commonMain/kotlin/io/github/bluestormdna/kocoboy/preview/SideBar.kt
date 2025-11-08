package io.github.bluestormdna.kocoboy.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.bluestormdna.kocoboy.core.cartridge.EmptyCartridgeHeader
import io.github.bluestormdna.kocoboy.ui.main.SideBar

@Preview(showBackground = true)
@Composable
fun SideBarPreview() {
    SideBar(
        cartridgeHeader = EmptyCartridgeHeader(),
        onLoadBios = {},
        onLoadRom = {},
        onPowerSwitch = {},
        onThemeChange = {},
    )
}