package io.github.bluestormdna.kocoboy

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

actual fun createImageBitmapFromIntArray(intArray: IntArray, width: Int, height: Int): ImageBitmap {
    val bitmap = Bitmap.createBitmap(intArray, width, height, Bitmap.Config.ARGB_8888)
    return bitmap.asImageBitmap()
}