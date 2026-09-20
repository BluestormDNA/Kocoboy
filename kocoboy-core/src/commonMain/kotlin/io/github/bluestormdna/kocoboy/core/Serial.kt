package io.github.bluestormdna.kocoboy.core

import io.github.bluestormdna.kocoboy.host.Host

class Serial(private val host: Host, private val scheduler: Scheduler, private val timer: Timer) {

    private var sb = 0
    private var sc = 0

    fun read(address: Int): Byte = when (address) {
        0x01 -> sb.toByte()
        // bits 1 to 6 are not wired and read as 1
        else -> (sc or 0x7E).toByte()
    }

    fun write(address: Int, value: Byte) {
        if (address == 0x01) {
            sb = value.toInt() and 0xFF
            return
        }
        sc = value.toInt() and 0x81
        scheduler.cancel(Event.SERIAL)
        if (sc == TRANSFER_ON_INTERNAL_CLOCK) scheduler.scheduleAt(Event.SERIAL, transferEnd())
    }

    fun onTransferComplete(bus: Bus) {
        host.serial(sb.toByte())
        // Link Cable not connected (yet)
        sb = 0xFF
        sc = sc and 0x7F
        bus.requestInterrupt(SERIAL_INTERRUPT)
    }

    fun reset() {
        sb = 0
        sc = 0
    }

    // bits shift on the divider's grid, so a transfer aligns to the reset, not to the SC write
    private fun transferEnd(): Long {
        val toFirstEdge = BIT_CYCLES - timer.counter.mod(BIT_CYCLES)
        return scheduler.clock + toFirstEdge + (BITS - 1) * BIT_CYCLES
    }

    companion object {
        private const val BIT_CYCLES = 512L
        private const val BITS = 8
        private const val TRANSFER_ON_INTERNAL_CLOCK = 0x81
        private const val SERIAL_INTERRUPT: Byte = 0x8
    }
}
