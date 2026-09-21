package io.github.bluestormdna.kocoboy.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.interpretCPointer
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import platform.posix.memcpy

private val bitmap = Bitmap().apply {
    allocPixels(ImageInfo(160, 144, ColorType.N32, alphaType = ColorAlphaType.OPAQUE))
}

@OptIn(ExperimentalForeignApi::class)
private val pixels = interpretCPointer<ByteVar>(bitmap.peekPixels()!!.addr)

@OptIn(ExperimentalForeignApi::class)
actual fun createImageBitmapFromIntArray(intArray: IntArray, width: Int, height: Int): ImageBitmap {
    intArray.usePinned {
        memcpy(pixels, it.addressOf(0), (intArray.size * Int.SIZE_BYTES).convert())
    }
    bitmap.notifyPixelsChanged()
    return bitmap.asComposeImageBitmap()
}
