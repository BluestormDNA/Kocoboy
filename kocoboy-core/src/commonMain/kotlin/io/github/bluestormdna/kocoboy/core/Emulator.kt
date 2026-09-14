package io.github.bluestormdna.kocoboy.core

import io.github.bluestormdna.kocoboy.core.cartridge.CartridgeHeader
import io.github.bluestormdna.kocoboy.core.cartridge.DefaultCartridgeHeader
import io.github.bluestormdna.kocoboy.core.cartridge.EmptyCartridgeHeader
import io.github.bluestormdna.kocoboy.core.cartridge.resolveCartridgeType
import io.github.bluestormdna.kocoboy.host.Host
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource.Monotonic.markNow
import kotlin.time.measureTime
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
    private val ppu: PPU = PPU(host),
    private val apu: APU = APU(host),
    private val joypad: Joypad = Joypad(),
    private val timer: Timer = Timer(),
    private val bus: Bus = Bus(apu, joypad, timer, ppu),
    private val cpu: CPU = CPU(bus),
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
        cpu.reset()
        bus.reset()
        ppu.reset()
        timer.reset()
    }

    private val targetTime = 1.seconds / 60

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
            frameCycles = 0
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

    // overshoot of the last frame, carried into the next one
    private var frameCycles = 0

    private fun runFrame() {
        var cycles = frameCycles
        while (cycles < CYCLES_PER_FRAME) {
            val step = cpu.execute()
            cycles += step
            timer.update(step, bus)
            ppu.update(step, bus)
            apu.update(step)
            handleInterrupts()
        }
        frameCycles = cycles - CYCLES_PER_FRAME
    }

    private suspend fun CoroutineScope.runFrames() {
        while (internalPowerSwitch && isActive) {
            val startOfFrame = markNow()
            val frameTime = measureTime { runFrame() }

            val sleepTime = targetTime - frameTime - 3.milliseconds

            if (sleepTime.inWholeMilliseconds > 1) {
                // val preSleepTime = startOfFrame.elapsedNow()
                // delay doesn't have enough resolution so try to sleep less
                // and busy wait at the end
                // todo review this per platform as they seem to have differences
                // and actual/expect heuristics
                delay(sleepTime.inWholeMilliseconds / 2)
                // println("postSleepElapsed: ${startOfFrame.elapsedNow() - preSleepTime}")
            }

            // ("targetTime: $targetTime frameTime: $frameTime sleepTime: $sleepTime")
            // println("End of frame: ${startOfFrame.elapsedNow()}")

            while (startOfFrame.elapsedNow() < targetTime) {
                yield()
            }
        }
    }

    fun handleInputPress(input: JoypadInputs) {
        joypad.press(input.bits, bus)
    }

    fun handleInputRelease(input: JoypadInputs) {
        joypad.release(input.bits)
    }

    private fun handleInterrupts() {
        val interrupts = bus.interruptFlags.toInt() and bus.interruptEnabled.toInt()
        if (interrupts != 0) {
            val interrupt = interrupts.countTrailingZeroBits()
            cpu.handleInterrupt(interrupt)
        }
        cpu.updateIme()
    }

    companion object {
        const val CYCLES_PER_FRAME = 70224
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
