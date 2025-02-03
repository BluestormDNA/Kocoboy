package io.github.bluestormdna.kocoboy

import androidx.compose.ui.graphics.ImageBitmap

expect fun createImageBitmapFromIntArray(intArray: IntArray, width: Int, height: Int): ImageBitmap