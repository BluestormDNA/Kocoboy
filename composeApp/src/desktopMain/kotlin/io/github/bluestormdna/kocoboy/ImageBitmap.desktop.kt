package io.github.bluestormdna.kocoboy

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.nio.ByteBuffer
import java.nio.ByteOrder

actual fun createImageBitmapFromIntArray(
    intArray: IntArray,
    width: Int,
    height: Int
): ImageBitmap {
    val screenImageInfo = ImageInfo(160, 144, ColorType.N32, alphaType = ColorAlphaType.OPAQUE)

    val byteArray = intArray.toByteArray()

    return Bitmap().apply {
        installPixels(screenImageInfo, byteArray, 160 * 4)
    }.asComposeImageBitmap()
}

fun IntArray.toByteArray(): ByteArray {
    val byteBuffer = ByteBuffer
        .allocate(this.size * 4)
        .order(ByteOrder.LITTLE_ENDIAN)

    byteBuffer.asIntBuffer()
        .put(this)

    return byteBuffer.array()
}
