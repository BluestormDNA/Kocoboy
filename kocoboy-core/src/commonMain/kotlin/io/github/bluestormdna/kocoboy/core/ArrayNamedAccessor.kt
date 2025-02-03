package io.github.bluestormdna.kocoboy.core

import kotlin.reflect.KProperty

class ArrayNamedAccessor(
    private val array: ByteArray,
    private val index: Int,
) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): Byte {
        return array[index]
    }
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Byte) {
        array[index] = value
    }
}