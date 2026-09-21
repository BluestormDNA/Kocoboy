package io.github.bluestormdna.kocoboy.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import java.nio.ByteOrder
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.impl.BufferUtil

private val bitmap = Bitmap().apply {
    allocPixels(ImageInfo(160, 144, ColorType.N32, alphaType = ColorAlphaType.OPAQUE))
}

// A view over the bitmap's own memory, so a frame is written straight into it
private val pixels = BufferUtil
    .getByteBufferFromPointer(bitmap.peekPixels()!!.addr, bitmap.rowBytes * bitmap.height)
    .order(ByteOrder.LITTLE_ENDIAN)
    .asIntBuffer()

actual fun createImageBitmapFromIntArray(intArray: IntArray, width: Int, height: Int): ImageBitmap {
    pixels.put(0, intArray)
    bitmap.notifyPixelsChanged()
    return bitmap.asComposeImageBitmap()
}
