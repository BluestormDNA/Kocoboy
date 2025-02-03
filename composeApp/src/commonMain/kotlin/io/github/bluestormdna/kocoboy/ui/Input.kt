package io.github.bluestormdna.kocoboy.ui

import androidx.compose.ui.input.key.Key
import io.github.bluestormdna.kocoboy.core.JoypadInputs

val keyboardInputMap = mapOf(
    Key.DirectionUp to JoypadInputs.UP,
    Key.W to JoypadInputs.UP,
    Key.DirectionLeft to JoypadInputs.LEFT,
    Key.A to JoypadInputs.LEFT,
    Key.DirectionDown to JoypadInputs.DOWN,
    Key.S to JoypadInputs.DOWN,
    Key.DirectionRight to JoypadInputs.RIGHT,
    Key.D to JoypadInputs.RIGHT,
    Key.Z to JoypadInputs.A,
    Key.K to JoypadInputs.A,
    Key.X to JoypadInputs.B,
    Key.L to JoypadInputs.B,
    Key.P to JoypadInputs.START,
    Key.O to JoypadInputs.SELECT,
)