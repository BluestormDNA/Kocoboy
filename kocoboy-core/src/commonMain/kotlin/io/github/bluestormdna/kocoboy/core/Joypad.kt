package io.github.bluestormdna.kocoboy.core

import kotlin.experimental.and
import kotlin.experimental.inv
import kotlin.experimental.or

class Joypad {

    private var pad = IDLE
    private var buttons = IDLE

    fun press(b: Byte) {
        if ((b and PAD_MASK) == PAD_MASK) {
            pad = pad and (b and 0xF).inv()
        } else if ((b and BUTTON_MASK) == BUTTON_MASK) {
            buttons = buttons and (b and 0xF).inv()
        }
    }

    fun release(b: Byte) {
        if ((b and PAD_MASK) == PAD_MASK) {
            pad = pad or (b and 0xF)
        } else if ((b and BUTTON_MASK) == BUTTON_MASK) {
            buttons = buttons or b and 0xF
        }
    }

    fun update(bus: Bus) {
        val JOYP = bus.JOYP

        if (!isBit(4, JOYP)) { // DPAD
            bus.JOYP = ((JOYP and MODE_MASK) or pad)
            if (pad != IDLE) bus.requestInterrupt(JOYPAD_INTERRUPT)
            return
        }

        if (!isBit(5, JOYP)) { // Buttons
            bus.JOYP = ((JOYP and MODE_MASK) or buttons)
            if (buttons != IDLE) bus.requestInterrupt(JOYPAD_INTERRUPT)
            return
        }

        bus.JOYP = RESET
    }

    companion object {
        private const val JOYPAD_INTERRUPT: Byte = 0x10
        private const val PAD_MASK: Byte = 0x10
        private const val BUTTON_MASK: Byte = 0x20
        private const val UNSELECT_MASK = 0x30
        private const val MODE_MASK: Byte = 0xF0.toByte()
        private const val RESET: Byte = 0xFF.toByte()
        private const val IDLE: Byte = 0xF
    }
}