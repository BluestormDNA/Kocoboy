package io.github.bluestormdna.kocoboy.core

// I would really like to talk with someone at Jetbrains about
// Kotlin bitwise ops...

inline fun bitSet(n: Byte, v: Byte): Byte {
    // v = 1 << n;
    return (v.toInt() or (1 shl n.toInt())).toByte()
}

inline fun bitClear(n: Int, v: Byte): Byte {
    // v & ~(1 << n);
    return (v.toInt() and (1 shl n).inv()).toByte()
}

inline fun isBit(n: Int, v: Byte): Boolean {
    // (v >> n) & 1 == 1;
    return ((v.toInt() shr n) and 1) == 1
}
