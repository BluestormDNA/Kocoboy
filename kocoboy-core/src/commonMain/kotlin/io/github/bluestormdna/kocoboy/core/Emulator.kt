package io.github.bluestormdna.kocoboy.core

import io.github.bluestormdna.kocoboy.core.cartridge.CartridgeHeader
import io.github.bluestormdna.kocoboy.core.cartridge.DefaultCartridgeHeader
import io.github.bluestormdna.kocoboy.core.cartridge.EmptyCartridgeHeader
import io.github.bluestormdna.kocoboy.core.cartridge.resolveCartridgeType
import io.github.bluestormdna.kocoboy.host.Host
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource.Monotonic.markNow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

class Emulator(
    private val host: Host,
    private val scheduler: Scheduler = Scheduler(),
    private val ppu: PPU = PPU(host, scheduler),
    private val apu: APU = APU(host, scheduler),
    private val joypad: Joypad = Joypad(),
    private val timer: Timer = Timer(scheduler),
    private val bus: Bus = Bus(apu, joypad, timer, ppu),
    private val cpu: CPU = CPU(bus, scheduler),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
) {

    private val _poweredOn = MutableStateFlow(false)
    val poweredOn = _poweredOn.asStateFlow()

    private val _cartridgeHeader = MutableStateFlow<CartridgeHeader>(EmptyCartridgeHeader())
    val cartridgeHeader = _cartridgeHeader.asStateFlow()

    @Volatile
    private var internalPowerSwitch = false

    private var emulatorJob: Job = Job()

    fun loadRom(rom: ByteArray) {
        val cartridge = resolveCartridgeType(rom)
        val header = DefaultCartridgeHeader(rom)
        _cartridgeHeader.value = header
        bus.load(cartridge)
    }

    fun loadBios(bios: ByteArray) {
        bus.load(bios)
    }

    // Only for testing and profiling purposes
    fun debugReadByte(addr: Int): Int = bus.readByte(addr)

    fun powerOff() {
        internalPowerSwitch = false
        _poweredOn.value = false
    }

    private fun reset() {
        scheduler.reset()
        apu.start()
        cpu.reset()
        bus.reset()
        ppu.reset()
        timer.reset()
        joypad.reset()
    }

    private val framePeriod = 1.seconds * CYCLES_PER_FRAME / CPU_HZ
    private val spinTime = 2.milliseconds

    fun powerOn() = launchLoop { runFrames() }

    // Only for testing and profiling purposes
    fun runUncapped() = launchLoop {
        // per frame: a volatile read and a context walk are too expensive per instruction
        while (internalPowerSwitch && isActive) runFrame()
    }

    // never two loops over the same CPU and Bus, each one starts and ends on a reset machine
    private fun launchLoop(loop: suspend CoroutineScope.() -> Unit) {
        val previous = emulatorJob
        previous.cancel()
        emulatorJob = scope.launch {
            previous.join()
            reset()
            internalPowerSwitch = true
            _poweredOn.value = true
            try {
                loop()
            } finally {
                // powering off leaves a blank screen
                reset()
                internalPowerSwitch = false
                _poweredOn.value = false
            }
        }
    }

    private fun runFrame() {
        scheduler.frameEnd += CYCLES_PER_FRAME
        joypad.latch(bus)
        while (scheduler.clock < scheduler.frameEnd) {
            scheduler.advance(cpu.step())
            while (scheduler.clock >= scheduler.nextDeadline) dispatch(scheduler.pollDue())
        }
    }

    private fun dispatch(event: Int) {
        when (event) {
            Event.PPU_MODE -> ppu.onModeChange(bus)
            Event.TIMER_OVERFLOW -> timer.onOverflow()
            Event.TIMER_RELOAD -> timer.onReload(bus)
            Event.APU_SEQUENCER -> apu.onFrameSequencer()
            Event.APU_SAMPLE -> apu.onSample()
        }
    }

    private suspend fun CoroutineScope.runFrames() {
        var nextFrame = markNow() + framePeriod
        while (internalPowerSwitch && isActive) {
            runFrame()
            val left = -nextFrame.elapsedNow()
            if (left > spinTime) delay(left - spinTime)
            while (-nextFrame.elapsedNow() > Duration.ZERO) yield()
            val stalled = nextFrame.elapsedNow() > spinTime
            nextFrame = (if (stalled) markNow() else nextFrame) + framePeriod
        }
    }

    fun handleInputPress(input: JoypadInputs) {
        joypad.press(input.bits)
    }

    fun handleInputRelease(input: JoypadInputs) {
        joypad.release(input.bits)
    }

    companion object {
        const val CYCLES_PER_FRAME = 70224
        private const val CPU_HZ = 4_194_304
    }

    fun powerSwitch() {
        if (internalPowerSwitch) {
            powerOff()
        } else {
            powerOn()
            // runUncapped()
        }
    }
}
