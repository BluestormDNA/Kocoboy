package io.github.bluestormdna.kocoboy.core

import kotlin.experimental.and
import kotlin.experimental.inv
import kotlin.experimental.or

class Joypad {

    private var pad = IDLE
    private var buttons = IDLE

    private var pollingPad = false
    private var pollingButtons = false

    private var joyp: Byte = JOYPAD_IDLE

    fun press(b: Byte, bus: Bus) {
        if ((b and PAD_MASK) == PAD_MASK) {
            pad = pad and (b and 0xF).inv()
            if (pollingPad) {
                bus.requestInterrupt(JOYPAD_INTERRUPT)
            }
        } else if ((b and BUTTON_MASK) == BUTTON_MASK) {
            buttons = buttons and (b and 0xF).inv()
            if (pollingButtons) {
                bus.requestInterrupt(JOYPAD_INTERRUPT)
            }
        }
    }

    fun release(b: Byte) {
        if ((b and PAD_MASK) == PAD_MASK) {
            pad = pad or (b and 0xF)
        } else if ((b and BUTTON_MASK) == BUTTON_MASK) {
            buttons = buttons or b and 0xF
        }
    }

    fun write(value: Byte) {
        joyp = value and MODE_MASK
        pollingPad = value and PAD_MASK == 0.toByte()
        pollingButtons = value and BUTTON_MASK == 0.toByte()
    }

    fun read(): Byte {
        if (pollingPad) {
            return joyp or pad
        }

        if (pollingButtons) {
            return joyp or buttons
        }

        return JOYPAD_IDLE
    }

    companion object {
        private const val JOYPAD_INTERRUPT: Byte = 0x10
        private const val PAD_MASK: Byte = 0x10
        private const val BUTTON_MASK: Byte = 0x20
        private const val MODE_MASK: Byte = 0xF0.toByte()
        private const val JOYPAD_IDLE: Byte = 0xFF.toByte()
        private const val IDLE: Byte = 0xF
    }
}
