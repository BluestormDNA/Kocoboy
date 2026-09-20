package io.github.bluestormdna.kocoboy.core

class Timer(private val scheduler: Scheduler) {

    private var divBase: Long = -DIVIDER_AT_BOOT

    private var tima = 0
    private var timaAt: Long = 0

    private var tma = 0
    private var tac = 0
    private var tacEnabled = false
    private var tacFrequency = 0

    // the divider DIV, TIMA and the serial clock derive from
    val counter: Long get() = scheduler.clock - divBase

    private fun timerSignal(): Boolean =
        tacEnabled && (counter ushr SELECTED_BITS[tacFrequency]) and 1L == 1L

    private fun edgesSinceAnchor(): Int {
        if (!tacEnabled) return 0
        val period = TAC_PERIODS[tacFrequency]
        return (counter / period - (timaAt - divBase) / period).toInt()
    }

    private fun settle() {
        tima += edgesSinceAnchor()
        timaAt = scheduler.clock
    }

    fun onOverflow() {
        val at = scheduler.firedAt
        tima = 0
        timaAt = at
        scheduler.scheduleAt(Event.TIMER_RELOAD, at + RELOAD_DELAY)
    }

    fun onReload(bus: Bus) {
        tima = tma
        bus.requestInterrupt(TIMER_INTERRUPT)
        scheduleOverflow()
    }

    private fun incrementTima() {
        tima = (tima + 1) and 0xFF
        if (tima == 0) scheduler.schedule(Event.TIMER_RELOAD, RELOAD_DELAY)
    }

    private fun scheduleOverflow() {
        if (!tacEnabled) {
            scheduler.cancel(Event.TIMER_OVERFLOW)
            return
        }
        val period = TAC_PERIODS[tacFrequency]
        val edge = (timaAt - divBase) / period + (0x100 - tima)
        scheduler.scheduleAt(Event.TIMER_OVERFLOW, divBase + edge * period)
    }

    fun write(address: Int, value: Byte) {
        when (address) {
            4 -> {
                settle()
                val wasHigh = timerSignal()
                divBase = scheduler.clock
                if (wasHigh) incrementTima()
                scheduleOverflow()
            }
            5 -> {
                scheduler.cancel(Event.TIMER_RELOAD)
                tima = value.toInt() and 0xFF
                timaAt = scheduler.clock
                scheduleOverflow()
            }
            6 -> tma = value.toInt() and 0xFF
            7 -> {
                settle()
                val wasHigh = timerSignal()
                tac = value.toInt()
                tacEnabled = value.toInt() and 0x4 != 0
                tacFrequency = value.toInt() and 0x3
                if (wasHigh && !timerSignal()) incrementTima()
                scheduleOverflow()
            }
        }
    }

    fun read(address: Int): Byte = when (address) {
        4 -> (counter ushr 8).toByte()
        5 -> (tima + edgesSinceAnchor()).toByte()
        6 -> tma.toByte()
        7 -> (tac or 0xF8).toByte()
        else -> 0xFF.toByte()
    }

    fun reset() {
        divBase = scheduler.clock - DIVIDER_AT_BOOT
        tima = 0
        timaAt = scheduler.clock
        tma = 0
        tac = 0
        tacEnabled = false
        tacFrequency = 0
        scheduleOverflow()
    }

    companion object {
        private val TAC_PERIODS = intArrayOf(1024, 16, 64, 256)
        private val SELECTED_BITS = intArrayOf(9, 3, 5, 7)
        private const val RELOAD_DELAY = 4

        // the divider is mid count at hand over, DIV reads AB
        private const val DIVIDER_AT_BOOT = 0xABCCL
        private const val TIMER_INTERRUPT: Byte = 0x04
    }
}
