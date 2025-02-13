package io.github.bluestormdna.kocoboy.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bluestormdna.kocoboy.core.cartridge.CartridgeHeader
import io.github.bluestormdna.kocoboy.ui.dmg.ClassicGrayBody
import io.github.bluestormdna.kocoboy.ui.dmg.ColorTheme
import io.github.bluestormdna.kocoboy.ui.dmg.GameBoy
import io.github.bluestormdna.kocoboy.ui.dmg.themeList
import io.github.bluestormdna.kocoboy.ui.icons.FolderOpen
import io.github.bluestormdna.kocoboy.ui.icons.Power
import io.github.bluestormdna.kocoboy.ui.theme.LocalColorTheme

@Composable
fun SideBar(
    modifier: Modifier = Modifier,
    cartridgeHeader: CartridgeHeader,
    onLoadBios: () -> Unit,
    onLoadRom: () -> Unit,
    onPowerSwitch: () -> Unit,
    onThemeChange: (ColorTheme) -> Unit,
) {
    Column(
        modifier = modifier.fillMaxHeight().widthIn(max = 200.dp),
    ) {
        Text(
            text = "KocoBoy",
            fontSize = 24.sp,
            modifier = Modifier
                .background(ClassicGrayBody)
                .padding(8.dp)
                .padding(end = 64.dp)
                .fillMaxWidth()
        )
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            IconButton(onLoadRom) {
                Icon(FolderOpen, contentDescription = "Load")
            }
            IconButton(onPowerSwitch) {
                Icon(Power, contentDescription = "Power")
            }
        }
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Text(cartridgeHeader.name)
            Text(cartridgeHeader.type)
        }
        LazyColumn(
            modifier = Modifier.padding(horizontal = 32.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(items = themeList, key = { theme -> theme.body.value.toLong() }) { theme ->
                CompositionLocalProvider(LocalColorTheme provides theme) {
                    GameBoy(
                        modifier = Modifier.clickable { onThemeChange(theme) }
                    )
                }
            }
        }
    }
}