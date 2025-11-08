package io.github.bluestormdna.kocoboy.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bluestormdna.kocoboy.core.cartridge.CartridgeHeader
import io.github.bluestormdna.kocoboy.ui.dmg.ClassicGrayBody
import io.github.bluestormdna.kocoboy.ui.dmg.ColorTheme
import io.github.bluestormdna.kocoboy.ui.dmg.GameBoy
import io.github.bluestormdna.kocoboy.ui.dmg.UnitShape
import io.github.bluestormdna.kocoboy.ui.dmg.themeList
import io.github.bluestormdna.kocoboy.ui.icons.FolderOpen
import io.github.bluestormdna.kocoboy.ui.icons.Palette
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
        var showThemeList by remember { mutableStateOf(false) }
        Text(
            text = "KocoBoy",
            fontSize = 24.sp,
            modifier = Modifier
                .background(ClassicGrayBody)
                .padding(8.dp)
                .padding(end = 64.dp)
                .fillMaxWidth(),
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
            IconButton(onClick = { showThemeList = !showThemeList }) {
                Icon(Palette, contentDescription = "Theme selector")
            }
        }
        Column(
            modifier = Modifier.padding(8.dp),
        ) {
            Text(cartridgeHeader.name)
            Text(cartridgeHeader.type)
        }
        LazyColumn(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(
                items = if (showThemeList) themeList else emptyList(),
                key = { theme -> theme.body.value.toLong() },
            ) { theme ->
                CompositionLocalProvider(LocalColorTheme provides theme) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, color = theme.body, RoundedCornerShape(10.dp))
                            .clickable { onThemeChange(theme) }
                            .padding(32.dp)
                            .animateItem(),
                    ) {
                        GameBoy(
                            modifier = Modifier.shadow(8.dp, UnitShape),
                        )
                    }
                }
            }
        }
    }
}
