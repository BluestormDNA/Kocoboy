package io.github.bluestormdna.kocoboy

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

private val bitmap = Bitmap.createBitmap(160, 144, Bitmap.Config.ARGB_8888)

actual fun createImageBitmapFromIntArray(intArray: IntArray, width: Int, height: Int): ImageBitmap {
    bitmap.setPixels(intArray, 0, 160, 0, 0, 160, 144)
    return bitmap.asImageBitmap()
}
