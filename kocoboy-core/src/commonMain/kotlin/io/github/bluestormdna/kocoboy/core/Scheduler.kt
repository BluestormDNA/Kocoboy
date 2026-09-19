package io.github.bluestormdna.kocoboy.core

object Event {
    const val PPU = 0
    const val TIMER_OVERFLOW = 1
    const val TIMER_RELOAD = 2
    const val APU_SEQUENCER = 3
    const val FRAME_END = 4

    const val COUNT = 5
}

class Scheduler {

    var clock: Long = 0
        private set

    var nextDeadline: Long = Long.MAX_VALUE
        private set

    private var nextEvent = -1

    private val deadlines = LongArray(Event.COUNT) { Long.MAX_VALUE }

    var firedAt: Long = 0
        private set

    // Instructions run without the interrupt check below this, 0 sends the next one through step()
    var limit: Long = 0

    fun reset() {
        clock = 0
        deadlines.fill(Long.MAX_VALUE)
        firedAt = 0
        limit = 0
        nextDeadline = Long.MAX_VALUE
        nextEvent = -1
    }

    fun reschedule(event: Int, period: Int) {
        scheduleAt(event, firedAt + period)
    }

    fun schedule(event: Int, cyclesFromNow: Int) {
        scheduleAt(event, clock + cyclesFromNow)
    }

    fun scheduleAt(event: Int, deadline: Long) {
        val previous = deadlines[event]
        deadlines[event] = deadline
        when {
            deadline < nextDeadline -> {
                nextDeadline = deadline
                nextEvent = event
                if (deadline < limit) limit = deadline
            }
            previous == nextDeadline -> recomputeNext()
        }
    }

    fun cancel(event: Int) {
        val previous = deadlines[event]
        deadlines[event] = Long.MAX_VALUE
        if (previous == nextDeadline) recomputeNext()
    }

    fun advance(cycles: Int) {
        clock += cycles
    }

    fun pollDue(): Int {
        val due = nextEvent
        firedAt = nextDeadline
        deadlines[due] = Long.MAX_VALUE
        recomputeNext()
        return due
    }

    private fun recomputeNext() {
        var min = Long.MAX_VALUE
        var at = -1
        for (i in 0..<Event.COUNT) {
            val d = deadlines[i]
            if (d < min) {
                min = d
                at = i
            }
        }
        nextDeadline = min
        nextEvent = at
    }
}
