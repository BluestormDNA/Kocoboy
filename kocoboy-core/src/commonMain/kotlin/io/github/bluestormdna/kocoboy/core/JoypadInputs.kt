package io.github.bluestormdna.kocoboy.core

enum class JoypadInputs(val bits: Byte) {
    RIGHT(0x11),
    LEFT(0x12),
    UP(0x14),
    DOWN(0x18),
    A(0x21),
    B(0x22),
    SELECT(0x24),
    START(0x28),
}