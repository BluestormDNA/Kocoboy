package io.github.bluestormdna.kocoboy.core

// I would really like to talk with someone at Jetbrains about
// Kotlin bitwise ops...

inline fun isBit(n: Int, v: Byte): Boolean {
    // (v >> n) & 1 == 1;
    return ((v.toInt() shr n) and 1) == 1
}
