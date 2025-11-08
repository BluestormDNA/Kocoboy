package io.github.bluestormdna.kocoboy

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

actual fun createImageBitmapFromIntArray(intArray: IntArray, width: Int, height: Int): ImageBitmap {
    val screenImageInfo = ImageInfo(160, 144, ColorType.N32, alphaType = ColorAlphaType.OPAQUE)

    val byteArray = intArray.toByteArray()

    return Bitmap().apply {
        installPixels(screenImageInfo, byteArray, 160 * 4)
    }.asComposeImageBitmap()
}

// todo
// https://youtrack.jetbrains.com/issue/KT-30098
fun IntArray.toByteArray(): ByteArray {
    val byteArray = ByteArray(this.size * 4)
    for (i in this.indices) {
        val color = this[i]
        byteArray[i * 4] = (color and 0xFF).toByte()
        byteArray[i * 4 + 1] = (color shr 8).toByte()
        byteArray[i * 4 + 2] = (color shr 16).toByte()
        byteArray[i * 4 + 3] = (color shr 24).toByte()
    }
    return byteArray
}
