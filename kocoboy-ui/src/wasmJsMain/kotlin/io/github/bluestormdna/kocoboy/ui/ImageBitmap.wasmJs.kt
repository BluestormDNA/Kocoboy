@file:OptIn(ExperimentalWasmJsInterop::class, UnsafeWasmMemoryApi::class)

package io.github.bluestormdna.kocoboy.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

private val bitmap = Bitmap().apply {
    allocPixels(ImageInfo(160, 144, ColorType.N32, alphaType = ColorAlphaType.OPAQUE))
}

private val address = bitmap.peekPixels()!!.addr

actual fun createImageBitmapFromIntArray(intArray: IntArray, width: Int, height: Int): ImageBitmap {
    val size = intArray.size * Int.SIZE_BYTES
    // Staged in our own linear memory so reaching skiko's is one copy, not a call per pixel
    withScopedMemoryAllocator { allocator ->
        val staging = allocator.allocate(size)
        for (i in intArray.indices) (staging + i * Int.SIZE_BYTES).storeInt(intArray[i])
        copyToSkiko(loadedWasm, staging.address.toInt(), address, size)
    }
    bitmap.notifyPixelsChanged()
    return bitmap.asComposeImageBitmap()
}

// Copies size bytes from our wasm memory at from into skiko's wasm memory at to
private fun copyToSkiko(loadedWasm: JsAny, from: Int, to: Int, size: Int): Unit = js(
    "new Uint8Array(loadedWasm._.memory.buffer, to, size).set(new Uint8Array(wasmExports.memory.buffer, from, size))",
)
