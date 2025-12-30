package io.github.bluestormdna.kocoboy

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.bluestormdna.kocoboy.ui.App

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "KocoBoy",
    ) {
        App()
    }
}
