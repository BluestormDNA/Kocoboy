package io.github.bluestormdna.kocoboy.core

import kotlin.concurrent.Volatile

class Joypad {

    @Volatile
    private var held = 0xFF

    private var latched = 0xFF
    private var select = 0

    fun press(b: Byte) {
        held = held and bit(b).inv()
    }

    fun release(b: Byte) {
        held = held or bit(b)
    }

    fun latch(bus: Bus) {
        val before = lines()
        latched = held
        raiseOnFall(before, bus)
    }

    fun write(value: Byte, bus: Bus) {
        val before = lines()
        select = value.toInt() and SELECT_MASK
        raiseOnFall(before, bus)
    }

    fun read(): Byte = (UNUSED_BITS or select or lines()).toByte()

    fun reset() {
        latched = held
        select = 0
    }

    private fun bit(b: Byte): Int {
        val bit = b.toInt() and 0xF
        return if (b.toInt() and BUTTON_SELECT != 0) bit shl 4 else bit
    }

    private fun lines(): Int {
        var lines = 0xF
        if (select and PAD_SELECT == 0) lines = lines and latched
        if (select and BUTTON_SELECT == 0) lines = lines and (latched ushr 4)
        return lines and 0xF
    }

    private fun raiseOnFall(before: Int, bus: Bus) {
        if (before and lines().inv() != 0) bus.requestInterrupt(JOYPAD_INTERRUPT)
    }

    companion object {
        private const val JOYPAD_INTERRUPT: Byte = 0x10
        private const val PAD_SELECT = 0x10
        private const val BUTTON_SELECT = 0x20
        private const val SELECT_MASK = 0x30
        private const val UNUSED_BITS = 0xC0
    }
}
