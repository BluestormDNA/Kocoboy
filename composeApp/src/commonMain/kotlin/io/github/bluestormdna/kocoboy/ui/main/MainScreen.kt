package io.github.bluestormdna.kocoboy.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bluestormdna.kocoboy.core.cartridge.CartridgeHeader
import io.github.bluestormdna.kocoboy.ui.dmg.ClassicGrayBody
import io.github.bluestormdna.kocoboy.ui.icons.FolderOpen
import io.github.bluestormdna.kocoboy.ui.icons.Power

@Composable
fun EmulatorSettings(
    modifier: Modifier = Modifier,
    cartridgeHeader: CartridgeHeader,
    onLoadBios: () -> Unit,
    onLoadRom: () -> Unit,
    onPowerSwitch: () -> Unit,
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
            Text("MBC: ${cartridgeHeader.mbc}")
        }
    }
}