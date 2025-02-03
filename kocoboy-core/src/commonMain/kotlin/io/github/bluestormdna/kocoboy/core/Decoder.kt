package io.github.bluestormdna.kocoboy.core

import kotlin.jvm.JvmInline

@JvmInline
value class Decoder(private val opcode: Int) {
    val to get() = opcode shr 3 and 0x7
    val from get() = opcode and 0x7
    val pair get() = opcode shr 4 and 0x3
    val aluOpIndex get() = opcode shr 3 and 0x7
    val rstAddress get() = opcode and 0x38
    val rotOpIndex get() = opcode shr 3 and 0x7
    val bitOpIndex get() = opcode shr 6 and 0x3
    val bitOpBit get() = opcode shr 3 and 0x7
}