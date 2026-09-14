package io.github.bluestormdna.kocoboy.core

import io.github.bluestormdna.kocoboy.host.Host
import java.io.File
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.measureTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * Throughput benchmark for the emulation core.
 */
class EmulatorBenchmark {

    private class CountingHost : Host {
        @Volatile
        var frames = 0L

        override fun render(frameBuffer: IntArray) {
            frames++
        }

        override fun play(sampleBuffer: ByteArray) = Unit
    }

    private suspend fun measure(rom: ByteArray): Double {
        val host = CountingHost()
        val emu = Emulator(host)

        emu.loadRom(rom)
        emu.runUncapped()

        delay(WARMUP)
        val startFrames = host.frames
        val elapsed = measureTime { delay(RUN) }
        val frames = host.frames - startFrames
        emu.powerOff()
        return frames / elapsed.toDouble(DurationUnit.SECONDS)
    }

    @Test
    fun throughput() = runBlocking {
        val path = System.getenv("KOCOBOY_BENCH_ROM") ?: return@runBlocking
        val rom = File(path).readBytes()
        val runs = List(RUNS) { measure(rom) }.sorted()
        val median = runs[RUNS / 2]
        val spread = (runs.last() - runs.first()) / median * 100
        val name = File(path).name

        println(
            "BENCH $name median=%.0f fps (%.1fx realtime) min=%.0f max=%.0f spread=%.1f%%".format(
                median,
                median / GAME_BOY_FPS,
                runs.first(),
                runs.last(),
                spread,
            ),
        )
    }

    companion object {
        private const val RUNS = 5
        private val WARMUP = 2.seconds
        private val RUN = 5.seconds
        private const val GAME_BOY_FPS = 59.7275
    }
}
