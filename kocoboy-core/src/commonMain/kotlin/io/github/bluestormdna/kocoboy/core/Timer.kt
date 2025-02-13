package io.github.bluestormdna.kocoboy.core

class Timer {

    private var divCounter = 0
    private var timerCounter = 0

    // Timer IO Regs
    //FF04 - DIV - Divider Register (R/W)
    private var div: Byte = 0

    //FF05 - TIMA - Timer counter (R/W)
    private var tima: Byte = 0

    //FF06 - TMA - Timer Modulo (R/W)
    private var tma: Byte = 0

    //FF07 - TAC - Timer Control (R/W)
    private var tac: Byte = 0
    private var tacEnabled: Boolean = false
    private var tacFrequency: Int = 0


    fun update(cycles: Int, bus: Bus) {
        handleDivider(cycles)
        handleTimer(cycles, bus)
    }

    private fun handleDivider(cycles: Int) {
        divCounter += cycles
        while (divCounter >= DMG_DIV_FREQ) {
            divCounter -= DMG_DIV_FREQ
            div++
        }
    }

    private fun handleTimer(cycles: Int, bus: Bus) {
        if (tacEnabled) {
            timerCounter += cycles
            while (timerCounter >= TAC_FREQUENCIES[tacFrequency]) {
                timerCounter -= TAC_FREQUENCIES[tacFrequency]
                tima++

                if (tima == TRIGGER) {
                    bus.requestInterrupt(TIMER_INTERRUPT)
                    tima = tma
                }
            }
        }
    }

    fun write(address: Int, value: Byte) {
        when(address) {
            4 -> {
                div = 0
                divCounter = 0
                timerCounter = 0
            }
            5 -> tima = value
            6 -> tma = value
            7 -> {
                tac = value
                tacEnabled = value.toInt() and 0x4 != 0
                tacFrequency = value.toInt() and 0x3
            }
        }
    }

    fun read(address: Int): Byte {
        return when(address) {
            4 -> div
            5 -> tima
            6 -> tma
            7 -> tac
            else -> 0xFF.toByte()
        }
    }

    fun reset() {
        divCounter = 0
        timerCounter = 0
    }

    companion object {
        private const val DMG_DIV_FREQ = 256        //16384Hz
        private val TAC_FREQUENCIES = arrayOf(1024, 16, 64, 256)
        //00: CPU Clock / 1024 (DMG, CGB:   4096 Hz, SGB:   ~4194 Hz)
        //01: CPU Clock / 16   (DMG, CGB: 262144 Hz, SGB: ~268400 Hz)
        //10: CPU Clock / 64   (DMG, CGB:  65536 Hz, SGB:  ~67110 Hz)
        //11: CPU Clock / 256  (DMG, CGB:  16384 Hz, SGB:  ~16780 Hz)
        // Bit 2: Timer    Interrupt Request (INT 50h)  (1=Request)
        private const val TIMER_INTERRUPT: Byte = 0x04
        private const val TRIGGER: Byte = 0xFF.toByte()
    }

}