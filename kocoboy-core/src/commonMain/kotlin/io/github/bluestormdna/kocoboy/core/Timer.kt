package io.github.bluestormdna.kocoboy.core

class Timer {

    private var divCounter = 0
    private var timerCounter = 0

    fun update(cycles: Int, bus: Bus) {
        handleDivider(cycles, bus)
        handleTimer(cycles, bus)
    }

    private fun handleDivider(cycles: Int, bus: Bus) {
        divCounter += cycles
        while (divCounter >= DMG_DIV_FREQ) {
            divCounter -= DMG_DIV_FREQ
            bus.DIV++
        }
    }

    private fun handleTimer(cycles: Int, bus: Bus) {
        if (bus.TAC_ENABLED) {
            timerCounter += cycles
            while (timerCounter >= TAC_FREQ[bus.TAC_FREQ]) {
                timerCounter -= TAC_FREQ[bus.TAC_FREQ]
                bus.TIMA++

                if (bus.TIMA == TRIGGER) {
                    bus.requestInterrupt(TIMER_INTERRUPT)
                    bus.TIMA = bus.TMA
                }
            }
        }
    }

    fun reset() {
        divCounter = 0
        timerCounter = 0
    }

    companion object {
        private const val DMG_DIV_FREQ = 256        //16384Hz
        private val TAC_FREQ = arrayOf(1024, 16, 64, 256)
        //00: CPU Clock / 1024 (DMG, CGB:   4096 Hz, SGB:   ~4194 Hz)
        //01: CPU Clock / 16   (DMG, CGB: 262144 Hz, SGB: ~268400 Hz)
        //10: CPU Clock / 64   (DMG, CGB:  65536 Hz, SGB:  ~67110 Hz)
        //11: CPU Clock / 256  (DMG, CGB:  16384 Hz, SGB:  ~16780 Hz)
        // Bit 2: Timer    Interrupt Request (INT 50h)  (1=Request)
        private const val TIMER_INTERRUPT: Byte = 0x04
        private const val TRIGGER: Byte = 0xFF.toByte()
    }

}