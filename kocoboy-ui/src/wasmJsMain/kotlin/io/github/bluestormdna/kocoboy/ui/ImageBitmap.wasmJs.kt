@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.bluestormdna.kocoboy.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int32Array
import org.khronos.webgl.set

private val bitmap = Bitmap().apply {
    allocPixels(ImageInfo(160, 144, ColorType.N32, alphaType = ColorAlphaType.OPAQUE))
}

private val address = bitmap.peekPixels()!!.addr

actual fun createImageBitmapFromIntArray(intArray: IntArray, width: Int, height: Int): ImageBitmap {
    // A fresh view every frame since growing skiko's heap detaches the old buffer
    val pixels = Int32Array(skikoMemory(loadedWasm), address, intArray.size)
    for (i in intArray.indices) pixels[i] = intArray[i]
    bitmap.notifyPixelsChanged()
    return bitmap.asComposeImageBitmap()
}

private fun skikoMemory(loadedWasm: JsAny): ArrayBuffer = js("loadedWasm._.memory.buffer")
