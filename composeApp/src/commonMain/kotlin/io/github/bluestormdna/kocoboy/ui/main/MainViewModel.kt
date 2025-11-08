package io.github.bluestormdna.kocoboy.ui.main

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.bluestormdna.kocoboy.AudioPlayer
import io.github.bluestormdna.kocoboy.core.Emulator
import io.github.bluestormdna.kocoboy.core.JoypadInputs
import io.github.bluestormdna.kocoboy.createImageBitmapFromIntArray
import io.github.bluestormdna.kocoboy.host.Host
import io.github.bluestormdna.kocoboy.platformAudioPlayer
import io.github.bluestormdna.kocoboy.ui.dmg.ClassicColorTheme
import io.github.bluestormdna.kocoboy.ui.dmg.ClassicScreenTheme
import io.github.bluestormdna.kocoboy.ui.dmg.ColorTheme
import io.github.bluestormdna.kocoboy.ui.dmg.ScreenTheme
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(private val platformAudioPlayer: AudioPlayer) : ViewModel() {

    private val _colorTheme = MutableStateFlow<ColorTheme>(ClassicColorTheme)
    val colorTheme = _colorTheme.asStateFlow()

    private val _screenTheme = MutableStateFlow<ScreenTheme>(ClassicScreenTheme)
    val screenTheme = _screenTheme.asStateFlow()

    private var currentFrameCounter = 0

    val vps = MutableStateFlow(0)

    init {
        initFpsCounter()
    }

    private fun initFpsCounter() = viewModelScope.launch {
        while (true) {
            delay(1000)
            vps.value = currentFrameCounter
            currentFrameCounter = 0
        }
    }

    private val host = object : Host {
        override fun render(frameBuffer: IntArray) {
            currentFrameCounter++
            frame.value = createImageBitmapFromIntArray(frameBuffer, SCREEN_WIDTH, SCREEN_HEIGHT)
        }

        override fun play(sampleBuffer: ByteArray) {
            platformAudioPlayer.play(sampleBuffer)
        }
    }

    private val emu = Emulator(host)

    val poweredOn = emu.poweredOn

    val cartridgeHeader = emu.cartridgeHeader

    val frame = MutableStateFlow(ImageBitmap(SCREEN_WIDTH, SCREEN_HEIGHT))

    fun loadBios(rom: ByteArray) = emu.loadBios(rom)

    fun loadRom(rom: ByteArray) = emu.loadRom(rom)

    fun loadRom(rom: PlatformFile) = viewModelScope.launch {
        val bytes = rom.readBytes()
        emu.loadRom(bytes)
    }

    fun handlePress(input: JoypadInputs) = emu.handleInputPress(input)

    fun handleRelease(input: JoypadInputs) = emu.handleInputRelease(input)

    fun powerSwitch() = emu.powerSwitch()

    fun themeChange(colorTheme: ColorTheme) {
        _colorTheme.value = colorTheme
    }

    companion object {
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MainViewModel(
                    platformAudioPlayer = platformAudioPlayer(),
                )
            }
        }

        const val SCREEN_WIDTH = 160
        const val SCREEN_HEIGHT = 144
    }
}
