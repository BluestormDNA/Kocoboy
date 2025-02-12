package io.github.bluestormdna.kocoboy.core

import io.github.bluestormdna.kocoboy.core.cartridge.CartridgeHeader
import io.github.bluestormdna.kocoboy.core.cartridge.DefaultCartridgeHeader
import io.github.bluestormdna.kocoboy.core.cartridge.EmptyCartridgeHeader
import io.github.bluestormdna.kocoboy.core.cartridge.resolveCartridgeType
import io.github.bluestormdna.kocoboy.host.Host
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource.Monotonic.markNow
import kotlin.time.measureTime

class Emulator(
    private val host: Host,
    private val ppu: PPU = PPU(host),
    private val apu: APU = APU(host),
    private val joypad: Joypad = Joypad(),
    private val bus: Bus = Bus(apu, joypad),
    private val timer: Timer = Timer(),
    private val cpu: CPU = CPU(bus),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
) {

    private val _poweredOn = MutableStateFlow(false)
    val poweredOn = _poweredOn.asStateFlow()

    private val _cartridgeHeader = MutableStateFlow<CartridgeHeader>(EmptyCartridgeHeader())
    val cartridgeHeader = _cartridgeHeader.asStateFlow()

    // Todo check the overhead of stateflow.value access on a tight loop so we can remove this
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

    private var cycleCounter = 0

    private val targetTime = 1.seconds / 60

    // todo: actual kmp lock this...
    fun powerOn() {
        emulatorJob.cancel()
        emulatorJob = scope.launch {
            reset()
            internalPowerSwitch = true
            _poweredOn.value = true

            var frameCycles = 0
            while (internalPowerSwitch) {
                val startOfFrame = markNow()
                val frameTime = measureTime {
                    while (frameCycles < 70224) {
                        val cycles = cpu.execute()
                        frameCycles += cycles
                        timer.update(cycles, bus)
                        ppu.update(cycles, bus)
                        apu.update(cycles)
                        handleInterrupts()
                        cycleCounter++
                    }
                    frameCycles -= 70224
                }

                val sleepTime = targetTime - frameTime - 3.milliseconds

                if (sleepTime.inWholeMilliseconds > 1) {
                    //val preSleepTime = startOfFrame.elapsedNow()
                    // delay doesn't have enough resolution so try to sleep less
                    // and busy wait at the end
                    // todo review this per platform as they seem to have differences
                    // and actual/expect heuristics
                    delay(sleepTime.inWholeMilliseconds / 2)
                    //println("postSleepElapsed: ${startOfFrame.elapsedNow() - preSleepTime}")
                }

                //("targetTime: $targetTime frameTime: $frameTime sleepTime: $sleepTime")
                //println("End of frame: ${startOfFrame.elapsedNow()}")

                while (startOfFrame.elapsedNow() < targetTime) {
                    yield()
                }

                //println("targetTime: $targetTime frameTime: $frameTime sleepTime: $sleepTime")
                //println("End of frame: ${startOfFrame.elapsedNow()}")
            }

            reset()
        }.also {
            it.invokeOnCompletion {
                internalPowerSwitch = false
                _poweredOn.value = false
            }
        }
    }


    // Only for testing and profiling purposes
    fun runUncapped() {
        emulatorJob = scope.launch {
            internalPowerSwitch = true
            _poweredOn.value = true
            while (internalPowerSwitch) {
                val cycles = cpu.execute()
                timer.update(cycles, bus)
                ppu.update(cycles, bus)
                apu.update(cycles)
                handleInterrupts()
            }
            reset()
        }.also {
            it.invokeOnCompletion {
                internalPowerSwitch = false
                _poweredOn.value = false
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
            for (i in 0..4) {
                if ((interrupts shr i and 1) == 1) {
                    cpu.handleInterrupt(i)
                }
            }
        }
        cpu.updateIme()
    }

    fun powerSwitch() {
        if (internalPowerSwitch) {
            powerOff()
        } else {
            powerOn()
        }
    }
}