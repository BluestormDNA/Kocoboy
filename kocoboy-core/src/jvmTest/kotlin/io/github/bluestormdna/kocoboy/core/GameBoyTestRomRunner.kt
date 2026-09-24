package io.github.bluestormdna.kocoboy.core

import io.github.bluestormdna.kocoboy.host.Host
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.fail
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

// Headless runner for Mooneye/Blargg test ROMs against the real Emulator (no mocks).
private class HeadlessHost : Host {
    val output = ByteArrayOutputStream()

    @Volatile var framesToScreen = 0

    @Volatile var screen: IntArray? = null

    // Copied on the emulator thread, so it is always a whole frame
    override fun render(frameBuffer: IntArray) {
        if (framesToScreen > 0 && --framesToScreen == 0) screen = frameBuffer.copyOf()
    }
    override fun play(sampleBuffer: ByteArray) {}
    override fun serial(byte: Byte) = output.write(byte.toInt())
}

enum class RomResult { PASS, FAIL, INCONCLUSIVE }

private val MOONEYE_PASS = byteArrayOf(3, 5, 8, 13, 21, 34)
private val MOONEYE_FAIL = byteArrayOf(0x42, 0x42, 0x42, 0x42, 0x42, 0x42)
private val BLARGG_SIGNATURE = byteArrayOf(0xDE.toByte(), 0xB0.toByte(), 0x61.toByte())
private const val BLARGG_STATUS_ADDR = 0xA000
private const val BLARGG_RUNNING = 0x80

private fun serialResultFor(serialOutput: ByteArray): RomResult {
    val lastSix = serialOutput.takeLast(6).toByteArray()
    if (lastSix.contentEquals(MOONEYE_PASS)) return RomResult.PASS
    if (lastSix.contentEquals(MOONEYE_FAIL)) return RomResult.FAIL

    val text = serialOutput.toString(Charsets.US_ASCII)
    return when {
        text.contains("passed", ignoreCase = true) -> RomResult.PASS
        text.contains("failed", ignoreCase = true) -> RomResult.FAIL
        else -> RomResult.INCONCLUSIVE
    }
}

private fun blarggMemoryResultFor(emulator: Emulator): RomResult {
    val signature = byteArrayOf(
        emulator.debugReadByte(BLARGG_STATUS_ADDR + 1).toByte(),
        emulator.debugReadByte(BLARGG_STATUS_ADDR + 2).toByte(),
        emulator.debugReadByte(BLARGG_STATUS_ADDR + 3).toByte(),
    )
    if (!signature.contentEquals(BLARGG_SIGNATURE)) return RomResult.INCONCLUSIVE

    val status = emulator.debugReadByte(BLARGG_STATUS_ADDR)
    return when {
        status == BLARGG_RUNNING -> RomResult.INCONCLUSIVE
        status == 0 -> RomResult.PASS
        else -> RomResult.FAIL
    }
}

class RomRun(val result: RomResult, val screen: IntArray?)

fun runRomWithScreen(rom: ByteArray, timeoutMillis: Long = 10_000): RomRun = runBlocking {
    val host = HeadlessHost()
    val emulator = Emulator(host)
    emulator.loadRom(rom)
    emulator.runUncapped()

    var result = RomResult.INCONCLUSIVE
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (System.currentTimeMillis() < deadline) {
        delay(50)
        val current = blarggMemoryResultFor(emulator)
            .takeIf { it != RomResult.INCONCLUSIVE }
            ?: serialResultFor(host.output.toByteArray())
        if (current != RomResult.INCONCLUSIVE) {
            result = current
            break
        }
    }
    // ROMs report before they finish printing, so the screen is taken 30 frames later
    host.framesToScreen = 30
    withTimeoutOrNull(1_000) { while (host.screen == null) delay(5) }
    emulator.powerOff()
    RomRun(result, host.screen)
}

fun runRom(rom: ByteArray): RomResult = runRomWithScreen(rom).result

/** Runs every .gb ROM under [root] (recursively), logging "path -> RESULT" as it completes. */
fun runRomsUnder(root: File): Map<String, RomResult> {
    val results = LinkedHashMap<String, RomResult>()
    root.walkTopDown()
        .filter { it.isFile && it.extension == "gb" }
        .sortedBy { it.relativeTo(root).path }
        .forEach { file ->
            val name = file.relativeTo(root).path
            val result = runRom(file.readBytes())
            println("$name -> $result")
            results[name] = result
        }
    return results
}

class GameBoyTestRomRunner {

    private fun runSuite(name: String) {
        val url = requireNotNull(javaClass.classLoader.getResource("roms/$name")) {
            "Missing test ROM resource directory: roms/$name"
        }
        println("=== $name ===")
        val results = runRomsUnder(File(url.toURI()))
        val counts = RomResult.entries.associateWith { r -> results.values.count { it == r } }
        println(
            "=== $name summary: ${counts[RomResult.PASS]} PASS / " +
                "${counts[RomResult.FAIL]} FAIL / " +
                "${counts[RomResult.INCONCLUSIVE]} INCONCLUSIVE " +
                "(${results.size} total) ===",
        )

        val failures = results.filterValues { it != RomResult.PASS }
        if (failures.isNotEmpty()) {
            val summary = failures.entries.joinToString("\n") { "${it.key} -> ${it.value}" }
            fail("Failed ROMs in $name (${failures.size}/${results.size}):\n$summary")
        }
    }

    @Test
    fun mooneye() = runSuite("mooneye")

    @Test
    fun blargg() = runSuite("blargg")
}
