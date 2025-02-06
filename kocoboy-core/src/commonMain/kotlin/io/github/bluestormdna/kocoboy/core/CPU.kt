@file:OptIn(ExperimentalStdlibApi::class)

package io.github.bluestormdna.kocoboy.core

import kotlin.experimental.and


@OptIn(ExperimentalStdlibApi::class)
class CPU(private val bus: Bus) {

    private var A: Int = 0
        set(value) {
            field = value and 0xFF
        }

    // We don't need to ensure 0..255 on F
    // as it is only accessed by flags or AF
    private var F: Int = 0

    private var B: Int = 0
        set(value) {
            field = value and 0xFF
        }
    private var C: Int = 0
        set(value) {
            field = value and 0xFF
        }

    private var D: Int = 0
        set(value) {
            field = value and 0xFF
        }
    private var E: Int = 0
        set(value) {
            field = value and 0xFF
        }

    private var H: Int = 0
        set(value) {
            field = value and 0xFF
        }
    private var L: Int = 0
        set(value) {
            field = value and 0xFF
        }

    private var flagZ: Boolean
        get() = F and 0x80 != 0
        set(value) {
            F = if (value) F or 0x80 else F and 0x80.inv()
        }

    private var flagN: Boolean
        get() = F and 0x40 != 0
        set(value) {
            F = if (value) F or 0x40 else F and 0x40.inv()
        }

    private var flagH: Boolean
        get() = F and 0x20 != 0
        set(value) {
            F = if (value) F or 0x20 else F and 0x20.inv()
        }

    private var flagC: Boolean
        get() = F and 0x10 != 0
        set(value) {
            F = if (value) F or 0x10 else F and 0x10.inv()
        }

    private var AF: Int
        get() = A shl 8 or F
        set(value) {
            A = value shr 8 and 0xFF
            F = value and 0xF0
        }

    private var BC: Int
        get() = B shl 8 or C
        set(value) {
            B = value shr 8 and 0xFF
            C = value and 0xFF
        }

    private var DE: Int
        get() = D shl 8 or E
        set(value) {
            D = value shr 8 and 0xFF
            E = value and 0xFF
        }

    private var HL: Int
        get() = H shl 8 or L
        set(value) {
            H = value shr 8 and 0xFF
            L = value and 0xFF
        }

    private var M: Int
        get() = bus.readByte(HL)
        set(value) {
            bus.writeByte(HL, value)
        }

    private var PC: Int = 0
        set(value) {
            field = value and 0xFFFF
        }

    private var SP: Int = 0
        set(value) {
            field = value and 0xFFFF
        }

    init {
        reset()
    }

    fun reset() {
        AF = 0x01B0
        BC = 0x0013
        DE = 0x00D8
        HL = 0x014d
        SP = 0xFFFE
        PC = 0x100
        ime = false
        imeEnabler = false
        halted = false
        haltBug = false
    }

    private var ime: Boolean = false
    private var imeEnabler: Boolean = false
    private var halted: Boolean = false
    private var haltBug: Boolean = false

    private inline fun Int.hi() = this shr 8 and 0xFF

    private inline fun Int.lo() = this and 0xFF

    private var cycles = 0

    private inline fun fetch(): Int {
        return bus.readByte(PC++)
    }

    private var debug = false

    fun execute(): Int {
        //if (debug) {
        //    //val line = generateInstructionLog()
        //    //println(line)
        //}

        cycles = 0

        val opcode = fetch()

        when (opcode) {
            0x00 -> Unit
            0x01 -> BC = fetchWord()
            0x02 -> bus.writeByte(BC, A)
            0x03 -> BC++
            0x04 -> B = incReg8(B)
            0x05 -> B = decReg8(B)
            0x06 -> B = fetch()
            0x07 -> rlca()

            0x08 -> {
                val imm16 = fetchWord()
                bus.writeByte(imm16, SP and 0xFF)
                bus.writeByte(imm16 + 1, SP shr 8 and 0xFF)
            }

            0x09 -> dad(BC)
            0x0A -> A = bus.readByte(BC)
            0x0B -> BC--
            0x0C -> C = incReg8(C)
            0x0D -> C = decReg8(C)
            0x0E -> C = fetch()
            0x0F -> rrca()

            0x10 -> stop()
            0x11 -> DE = fetchWord()
            0x12 -> bus.writeByte(DE, A)
            0x13 -> DE++
            0x14 -> D = incReg8(D)
            0x15 -> D = decReg8(D)
            0x16 -> D = fetch()
            0x17 -> rla()

            0x18 -> jr(true)
            0x19 -> dad(DE)
            0x1A -> A = bus.readByte(DE)
            0x1B -> DE--
            0x1C -> E = incReg8(E)
            0x1D -> E = decReg8(E)
            0x1E -> E = fetch()
            0x1F -> rra()

            0x20 -> jr(!flagZ)
            0x21 -> HL = fetchWord()
            0x22 -> bus.writeByte(HL++, A)
            0x23 -> HL++
            0x24 -> H = incReg8(H)
            0x25 -> H = decReg8(H)
            0x26 -> H = fetch()
            0x27 -> daa()

            0x28 -> jr(flagZ)
            0x29 -> dad(HL)
            0x2A -> A = bus.readByte(HL++)
            0x2B -> HL--
            0x2C -> L = incReg8(L)
            0x2D -> L = decReg8(L)
            0x2E -> L = fetch()
            0x2F -> cpl()

            0x30 -> jr(!flagC)
            0x31 -> SP = fetchWord()
            0x32 -> bus.writeByte(HL--, A)
            0x33 -> SP++
            0x34 -> M = incReg8(M)
            0x35 -> M = decReg8(M)
            0x36 -> M = fetch()
            0x37 -> scf()

            0x38 -> jr(flagC)
            0x39 -> dad(SP)
            0x3A -> A = bus.readByte(HL--)
            0x3B -> SP--
            0x3C -> A = incReg8(A)
            0x3D -> A = decReg8(A)
            0x3E -> A = fetch()
            0x3F -> ccf()

            0x40 -> Unit
            0x41 -> B = C
            0x42 -> B = D
            0x43 -> B = E
            0x44 -> B = H
            0x45 -> B = L
            0x46 -> B = M
            0x47 -> B = A

            0x48 -> C = B
            0x49 -> Unit
            0x4A -> C = D
            0x4B -> C = E
            0x4C -> C = H
            0x4D -> C = L
            0x4E -> C = M
            0x4F -> C = A

            0x50 -> D = B
            0x51 -> D = C
            0x52 -> Unit
            0x53 -> D = E
            0x54 -> D = H
            0x55 -> D = L
            0x56 -> D = M
            0x57 -> D = A

            0x58 -> E = B
            0x59 -> E = C
            0x5A -> E = D
            0x5B -> Unit
            0x5C -> E = H
            0x5D -> E = L
            0x5E -> E = M
            0x5F -> E = A

            0x60 -> H = B
            0x61 -> H = C
            0x62 -> H = D
            0x63 -> H = E
            0x64 -> Unit
            0x65 -> H = L
            0x66 -> H = M
            0x67 -> H = A

            0x68 -> L = B
            0x69 -> L = C
            0x6A -> L = D
            0x6B -> L = E
            0x6C -> L = H
            0x6D -> Unit
            0x6E -> L = M
            0x6F -> L = A

            0x70 -> M = B
            0x71 -> M = C
            0x72 -> M = D
            0x73 -> M = E
            0x74 -> M = H
            0x75 -> M = L
            0x76 -> halt()
            0x77 -> M = A

            0x78 -> A = B
            0x79 -> A = C
            0x7A -> A = D
            0x7B -> A = E
            0x7C -> A = H
            0x7D -> A = L
            0x7E -> A = M
            0x7F -> Unit

            0x80 -> add(B)
            0x81 -> add(C)
            0x82 -> add(D)
            0x83 -> add(E)
            0x84 -> add(H)
            0x85 -> add(L)
            0x86 -> add(M)
            0x87 -> add(A)

            0x88 -> adc(B)
            0x89 -> adc(C)
            0x8A -> adc(D)
            0x8B -> adc(E)
            0x8C -> adc(H)
            0x8D -> adc(L)
            0x8E -> adc(M)
            0x8F -> adc(A)

            0x90 -> sub(B)
            0x91 -> sub(C)
            0x92 -> sub(D)
            0x93 -> sub(E)
            0x94 -> sub(H)
            0x95 -> sub(L)
            0x96 -> sub(M)
            0x97 -> sub(A)

            0x98 -> sbc(B)
            0x99 -> sbc(C)
            0x9A -> sbc(D)
            0x9B -> sbc(E)
            0x9C -> sbc(H)
            0x9D -> sbc(L)
            0x9E -> sbc(M)
            0x9F -> sbc(A)

            0xA0 -> and(B)
            0xA1 -> and(C)
            0xA2 -> and(D)
            0xA3 -> and(E)
            0xA4 -> and(H)
            0xA5 -> and(L)
            0xA6 -> and(M)
            0xA7 -> and(A)

            0xA8 -> xor(B)
            0xA9 -> xor(C)
            0xAA -> xor(D)
            0xAB -> xor(E)
            0xAC -> xor(H)
            0xAD -> xor(L)
            0xAE -> xor(M)
            0xAF -> xor(A)

            0xB0 -> or(B)
            0xB1 -> or(C)
            0xB2 -> or(D)
            0xB3 -> or(E)
            0xB4 -> or(H)
            0xB5 -> or(L)
            0xB6 -> or(M)
            0xB7 -> or(A)

            0xB8 -> cp(B)
            0xB9 -> cp(C)
            0xBA -> cp(D)
            0xBB -> cp(E)
            0xBC -> cp(H)
            0xBD -> cp(L)
            0xBE -> cp(M)
            0xBF -> cp(A)

            0xC0 -> ret(!flagZ)
            0xC1 -> BC = pop()
            0xC2 -> jp(!flagZ)
            0xC3 -> jp(true) // PC = imm16
            0xC4 -> call(!flagZ)
            0xC5 -> push(BC)
            0xC6 -> add(fetch())
            0xC7 -> rst(0x0)

            0xC8 -> ret(flagZ)
            0xC9 -> PC = pop() // ret(true) Unconditional return shortcut as ret true adds cycles
            0xCA -> jp(flagZ)
            0xCB -> prefixCB()
            0xCC -> call(flagZ)
            0xCD -> call(true)
            0xCE -> adc(fetch())
            0xCF -> rst(0x8)

            0xD0 -> ret(!flagC)
            0xD1 -> DE = pop()
            0xD2 -> jp(!flagC)
            0xD3 -> Unit
            0xD4 -> call(!flagC)
            0xD5 -> push(DE)
            0xD6 -> sub(fetch())
            0xD7 -> rst(0x10)

            0xD8 -> ret(flagC)
            0xD9 -> {
                PC = pop() // ret(true) Unconditional return shortcut as ret true adds cycles
                ime = true
            }

            0xDA -> jp(flagC)
            0xDB -> Unit
            0xDC -> call(flagC)
            0xDD -> Unit
            0xDE -> sbc(fetch())
            0xDF -> rst(0x18)

            0xE0 -> bus.writeByte(0xFF00 + fetch(), A)
            0xE1 -> HL = pop()
            0xE2 -> bus.writeByte(0xFF00 + C, A)
            0xE3 -> Unit
            0xE4 -> Unit
            0xE5 -> push(HL)
            0xE6 -> and(fetch())
            0xE7 -> rst(0x20)

            0xE8 -> SP = addSigned8(SP, fetch())
            0xE9 -> PC = HL // Direct Jump
            0xEA -> bus.writeByte(fetchWord(), A)
            0xEB -> Unit
            0xEC -> Unit
            0xED -> Unit
            0xEE -> xor(fetch())
            0xEF -> rst(0x28)

            0xF0 -> A = bus.readByte(0xFF00 + fetch())
            0xF1 -> AF = pop()
            0xF2 -> A = bus.readByte(0xFF00 + C)
            0xF3 -> di()
            0xF4 -> Unit
            0xF5 -> push(AF)
            0xF6 -> or(fetch())
            0xF7 -> rst(0x30)

            0xF8 -> HL = addSigned8(SP, fetch())
            0xF9 -> SP = HL
            0xFA -> A = bus.readByte(fetchWord())
            0xFB -> ei()
            0xFC -> Unit
            0xFD -> Unit
            0xFE -> cp(fetch())
            0xFF -> rst(0x38)
        }

        cycles += CpuCycles.opcodeCycles[opcode]
        return cycles
    }

    private fun prefixCB() {
        val opcode = fetch()

        when (opcode) {
            0x00 -> B = rlc(B)
            0x01 -> C = rlc(C)
            0x02 -> D = rlc(D)
            0x03 -> E = rlc(E)
            0x04 -> H = rlc(H)
            0x05 -> L = rlc(L)
            0x06 -> M = rlc(M)
            0x07 -> A = rlc(A)

            0x08 -> B = rrc(B)
            0x09 -> C = rrc(C)
            0x0A -> D = rrc(D)
            0x0B -> E = rrc(E)
            0x0C -> H = rrc(H)
            0x0D -> L = rrc(L)
            0x0E -> M = rrc(M)
            0x0F -> A = rrc(A)

            0x10 -> B = rl(B)
            0x11 -> C = rl(C)
            0x12 -> D = rl(D)
            0x13 -> E = rl(E)
            0x14 -> H = rl(H)
            0x15 -> L = rl(L)
            0x16 -> M = rl(M)
            0x17 -> A = rl(A)

            0x18 -> B = rr(B)
            0x19 -> C = rr(C)
            0x1A -> D = rr(D)
            0x1B -> E = rr(E)
            0x1C -> H = rr(H)
            0x1D -> L = rr(L)
            0x1E -> M = rr(M)
            0x1F -> A = rr(A)

            0x20 -> B = sla(B)
            0x21 -> C = sla(C)
            0x22 -> D = sla(D)
            0x23 -> E = sla(E)
            0x24 -> H = sla(H)
            0x25 -> L = sla(L)
            0x26 -> M = sla(M)
            0x27 -> A = sla(A)

            0x28 -> B = sra(B)
            0x29 -> C = sra(C)
            0x2A -> D = sra(D)
            0x2B -> E = sra(E)
            0x2C -> H = sra(H)
            0x2D -> L = sra(L)
            0x2E -> M = sra(M)
            0x2F -> A = sra(A)

            0x30 -> B = swap(B)
            0x31 -> C = swap(C)
            0x32 -> D = swap(D)
            0x33 -> E = swap(E)
            0x34 -> H = swap(H)
            0x35 -> L = swap(L)
            0x36 -> M = swap(M)
            0x37 -> A = swap(A)

            0x38 -> B = srl(B)
            0x39 -> C = srl(C)
            0x3A -> D = srl(D)
            0x3B -> E = srl(E)
            0x3C -> H = srl(H)
            0x3D -> L = srl(L)
            0x3E -> M = srl(M)
            0x3F -> A = srl(A)

            0x40 -> bit(0x1, B)
            0x41 -> bit(0x1, C)
            0x42 -> bit(0x1, D)
            0x43 -> bit(0x1, E)
            0x44 -> bit(0x1, H)
            0x45 -> bit(0x1, L)
            0x46 -> bit(0x1, M)
            0x47 -> bit(0x1, A)

            0x48 -> bit(0x2, B)
            0x49 -> bit(0x2, C)
            0x4A -> bit(0x2, D)
            0x4B -> bit(0x2, E)
            0x4C -> bit(0x2, H)
            0x4D -> bit(0x2, L)
            0x4E -> bit(0x2, M)
            0x4F -> bit(0x2, A)

            0x50 -> bit(0x4, B)
            0x51 -> bit(0x4, C)
            0x52 -> bit(0x4, D)
            0x53 -> bit(0x4, E)
            0x54 -> bit(0x4, H)
            0x55 -> bit(0x4, L)
            0x56 -> bit(0x4, M)
            0x57 -> bit(0x4, A)

            0x58 -> bit(0x8, B)
            0x59 -> bit(0x8, C)
            0x5A -> bit(0x8, D)
            0x5B -> bit(0x8, E)
            0x5C -> bit(0x8, H)
            0x5D -> bit(0x8, L)
            0x5E -> bit(0x8, M)
            0x5F -> bit(0x8, A)

            0x60 -> bit(0x10, B)
            0x61 -> bit(0x10, C)
            0x62 -> bit(0x10, D)
            0x63 -> bit(0x10, E)
            0x64 -> bit(0x10, H)
            0x65 -> bit(0x10, L)
            0x66 -> bit(0x10, M)
            0x67 -> bit(0x10, A)

            0x68 -> bit(0x20, B)
            0x69 -> bit(0x20, C)
            0x6A -> bit(0x20, D)
            0x6B -> bit(0x20, E)
            0x6C -> bit(0x20, H)
            0x6D -> bit(0x20, L)
            0x6E -> bit(0x20, M)
            0x6F -> bit(0x20, A)

            0x70 -> bit(0x40, B)
            0x71 -> bit(0x40, C)
            0x72 -> bit(0x40, D)
            0x73 -> bit(0x40, E)
            0x74 -> bit(0x40, H)
            0x75 -> bit(0x40, L)
            0x76 -> bit(0x40, M)
            0x77 -> bit(0x40, A)

            0x78 -> bit(0x80, B)
            0x79 -> bit(0x80, C)
            0x7A -> bit(0x80, D)
            0x7B -> bit(0x80, E)
            0x7C -> bit(0x80, H)
            0x7D -> bit(0x80, L)
            0x7E -> bit(0x80, M)
            0x7F -> bit(0x80, A)

            0x80 -> B = res(0x1, B)
            0x81 -> C = res(0x1, C)
            0x82 -> D = res(0x1, D)
            0x83 -> E = res(0x1, E)
            0x84 -> H = res(0x1, H)
            0x85 -> L = res(0x1, L)
            0x86 -> M = res(0x1, M)
            0x87 -> A = res(0x1, A)

            0x88 -> B = res(0x2, B)
            0x89 -> C = res(0x2, C)
            0x8A -> D = res(0x2, D)
            0x8B -> E = res(0x2, E)
            0x8C -> H = res(0x2, H)
            0x8D -> L = res(0x2, L)
            0x8E -> M = res(0x2, M)
            0x8F -> A = res(0x2, A)

            0x90 -> B = res(0x4, B)
            0x91 -> C = res(0x4, C)
            0x92 -> D = res(0x4, D)
            0x93 -> E = res(0x4, E)
            0x94 -> H = res(0x4, H)
            0x95 -> L = res(0x4, L)
            0x96 -> M = res(0x4, M)
            0x97 -> A = res(0x4, A)

            0x98 -> B = res(0x8, B)
            0x99 -> C = res(0x8, C)
            0x9A -> D = res(0x8, D)
            0x9B -> E = res(0x8, E)
            0x9C -> H = res(0x8, H)
            0x9D -> L = res(0x8, L)
            0x9E -> M = res(0x8, M)
            0x9F -> A = res(0x8, A)

            0xA0 -> B = res(0x10, B)
            0xA1 -> C = res(0x10, C)
            0xA2 -> D = res(0x10, D)
            0xA3 -> E = res(0x10, E)
            0xA4 -> H = res(0x10, H)
            0xA5 -> L = res(0x10, L)
            0xA6 -> M = res(0x10, M)
            0xA7 -> A = res(0x10, A)

            0xA8 -> B = res(0x20, B)
            0xA9 -> C = res(0x20, C)
            0xAA -> D = res(0x20, D)
            0xAB -> E = res(0x20, E)
            0xAC -> H = res(0x20, H)
            0xAD -> L = res(0x20, L)
            0xAE -> M = res(0x20, M)
            0xAF -> A = res(0x20, A)

            0xB0 -> B = res(0x40, B)
            0xB1 -> C = res(0x40, C)
            0xB2 -> D = res(0x40, D)
            0xB3 -> E = res(0x40, E)
            0xB4 -> H = res(0x40, H)
            0xB5 -> L = res(0x40, L)
            0xB6 -> M = res(0x40, M)
            0xB7 -> A = res(0x40, A)

            0xB8 -> B = res(0x80, B)
            0xB9 -> C = res(0x80, C)
            0xBA -> D = res(0x80, D)
            0xBB -> E = res(0x80, E)
            0xBC -> H = res(0x80, H)
            0xBD -> L = res(0x80, L)
            0xBE -> M = res(0x80, M)
            0xBF -> A = res(0x80, A)

            0xC0 -> B = set(0x1, B)
            0xC1 -> C = set(0x1, C)
            0xC2 -> D = set(0x1, D)
            0xC3 -> E = set(0x1, E)
            0xC4 -> H = set(0x1, H)
            0xC5 -> L = set(0x1, L)
            0xC6 -> M = set(0x1, M)
            0xC7 -> A = set(0x1, A)

            0xC8 -> B = set(0x2, B)
            0xC9 -> C = set(0x2, C)
            0xCA -> D = set(0x2, D)
            0xCB -> E = set(0x2, E)
            0xCC -> H = set(0x2, H)
            0xCD -> L = set(0x2, L)
            0xCE -> M = set(0x2, M)
            0xCF -> A = set(0x2, A)

            0xD0 -> B = set(0x4, B)
            0xD1 -> C = set(0x4, C)
            0xD2 -> D = set(0x4, D)
            0xD3 -> E = set(0x4, E)
            0xD4 -> H = set(0x4, H)
            0xD5 -> L = set(0x4, L)
            0xD6 -> M = set(0x4, M)
            0xD7 -> A = set(0x4, A)

            0xD8 -> B = set(0x8, B)
            0xD9 -> C = set(0x8, C)
            0xDA -> D = set(0x8, D)
            0xDB -> E = set(0x8, E)
            0xDC -> H = set(0x8, H)
            0xDD -> L = set(0x8, L)
            0xDE -> M = set(0x8, M)
            0xDF -> A = set(0x8, A)

            0xE0 -> B = set(0x10, B)
            0xE1 -> C = set(0x10, C)
            0xE2 -> D = set(0x10, D)
            0xE3 -> E = set(0x10, E)
            0xE4 -> H = set(0x10, H)
            0xE5 -> L = set(0x10, L)
            0xE6 -> M = set(0x10, M)
            0xE7 -> A = set(0x10, A)

            0xE8 -> B = set(0x20, B)
            0xE9 -> C = set(0x20, C)
            0xEA -> D = set(0x20, D)
            0xEB -> E = set(0x20, E)
            0xEC -> H = set(0x20, H)
            0xED -> L = set(0x20, L)
            0xEE -> M = set(0x20, M)
            0xEF -> A = set(0x20, A)

            0xF0 -> B = set(0x40, B)
            0xF1 -> C = set(0x40, C)
            0xF2 -> D = set(0x40, D)
            0xF3 -> E = set(0x40, E)
            0xF4 -> H = set(0x40, H)
            0xF5 -> L = set(0x40, L)
            0xF6 -> M = set(0x40, M)
            0xF7 -> A = set(0x40, A)

            0xF8 -> B = set(0x80, B)
            0xF9 -> C = set(0x80, C)
            0xFA -> D = set(0x80, D)
            0xFB -> E = set(0x80, E)
            0xFC -> H = set(0x80, H)
            0xFD -> L = set(0x80, L)
            0xFE -> M = set(0x80, M)
            0xFF -> A = set(0x80, A)
        }

        cycles += CpuCycles.opcodeCBCycles[opcode]
    }

    private fun halt() {
        if (!ime) {
            val flags = bus.interruptFlags and bus.interruptFlags
            if ((flags and 0x1F) == 0.toByte()) {
                halted = true
                PC--
            } else {
                haltBug = true
            }
        }
    }

    fun handleInterrupt(b: Int) {
        if (halted) {
            PC++
            halted = false
        }
        if (ime) {
            push(PC)
            PC = (0x40 + (8 * b))
            ime = false
            bus.clearInterrupt(b)
        }
    }

    fun updateIme() {
        ime = ime or imeEnabler
        imeEnabler = false
    }

    private fun addSigned8(register: Int, value: Int): Int {
        F = 0
        //flagZ = false
        //flagN = false
        flagH = ((register and 0xF) + (value and 0xF)) > 0xF
        flagC = ((register and 0xFF) + value) shr 8 and 0xFF != 0
        return (register + value.toByte()) and 0xFFFF
    }

    private fun ccf() {
        flagC = !flagC
        flagN = false
        flagH = false
    }

    private fun scf() {
        flagC = true
        flagN = false
        flagH = false
    }

    private fun daa() {
        if (flagN) { // sub
            if (flagC) {
                A -= 0x60
            }
            if (flagH) {
                A -= 0x6
            }
        } else { // add
            if (flagC || (A > 0x99)) {
                A += 0x60
                flagC = true
            }
            if (flagH || (A and 0xF) > 0x9) {
                A += 0x6
            }
        }
        flagZ = A == 0
        flagH = false
    }

    private fun cpl() {
        A = A.inv()
        flagN = true
        flagH = true
    }

    private fun ei() {
        imeEnabler = true
    }

    private fun di() {
        ime = false
    }

    private fun rst(b: Int) {
        push(PC)
        PC = b
    }

    private fun jp(flag: Boolean) {
        if (flag) {
            PC = fetchWord()
            cycles += CpuCycles.ControlFlowCycles.JP
        } else {
            PC += 2
        }
    }

    private fun ret(flag: Boolean) {
        if (flag) {
            PC = pop()
            cycles += CpuCycles.ControlFlowCycles.RET
        }
    }

    private fun rra() {
        val prevC = flagC
        F = 0
        flagC = A and 0x01 != 0
        A = (A shr 1) or (if (prevC) 0x80 else 0)
    }

    private fun rla() {
        val prevC = flagC
        F = 0
        flagC = A and 0x80 != 0
        A = (A shl 1) or (if (prevC) 1 else 0)
    }

    private fun rrca() {
        F = 0
        flagC = A and 0x01 != 0
        A = (A shr 1) or (A shl 7) and 0xFF
    }

    private fun rlca() {
        F = 0
        flagC = A and 0x80 != 0
        A = (A shl 1) or (A shr 7) and 0xFF
    }

    private fun jr(flag: Boolean) {
        if (flag) {
            val rel = fetch().toByte()
            PC += rel
            cycles += CpuCycles.ControlFlowCycles.JR
        } else {
            PC++
        }
    }

    private fun dad(value: Int) {
        val result = HL + value
        flagN = false
        flagH = ((HL and 0xFFF) + (value and 0xFFF)) > 0xFFF
        flagC = result shr 16 and 0xFFFF != 0
        HL = result
    }

    private fun fetchWord(): Int {
        val lo = fetch()
        val hi = fetch()
        return hi shl 8 or lo
    }

    private fun incReg8(value: Int): Int {
        val result = value + 1
        flagZ = result and 0xFF == 0
        flagN = false
        flagH = ((value and 0xF) + (1 and 0xF)) > 0xF
        return result
    }

    private fun decReg8(value: Int): Int {
        val result = value - 1
        flagZ = result and 0xFF == 0
        flagN = true
        flagH = (value and 0xF) < (1 and 0xF)
        return result
    }

    private fun stop() {
        println("Opcode: STOP")
    }

    private fun add(value: Int) {
        val result = A + value
        F = 0
        flagZ = result and 0xFF == 0
        //flagN = false
        flagH = ((A and 0xF) + (value and 0xF)) > 0xF
        flagC = result shr 8 and 0xFF != 0
        A = result
    }

    private fun adc(value: Int) {
        val carry = if (flagC) 1 else 0
        val result = A + value + carry
        F = 0
        flagZ = result and 0xFF == 0
        //flagN = false
        flagH = ((A and 0xF) + (value and 0xF) + carry) > 0xF
        flagC = result shr 8 and 0xFF != 0
        A = result
    }

    private fun sub(value: Int) {
        val result = A - value
        flagZ = result and 0xFF == 0
        flagN = true
        flagH = (A and 0xF) < (value and 0xF)
        flagC = result shr 8 and 0xFF != 0
        A = result
    }

    private fun sbc(value: Int) {
        val carry = if (flagC) 1 else 0
        val result = A - value - carry
        flagZ = result and 0xFF == 0
        flagN = true
        flagH = (A and 0xF) < (value and 0xF) + carry
        flagC = result shr 8 and 0xFF != 0
        A = result
    }

    private fun and(value: Int) {
        val result = A and value
        F = 0
        flagZ = result and 0xFF == 0
        //flagN = false
        flagH = true
        //flagC = false
        A = result
    }

    private fun xor(value: Int) {
        val result = A xor value
        F = 0
        flagZ = result and 0xFF == 0
        //flagN = false
        //flagH = false
        //flagC = false
        A = result
    }

    private fun or(value: Int) {
        val result = A or value
        F = 0
        flagZ = result and 0xFF == 0
        //flagN = false
        //flagH = false
        //flagC = false
        A = result
    }

    private fun cp(value: Int) {
        val result = A - value
        flagZ = result and 0xFF == 0
        flagN = true
        flagH = (A and 0xF) < (value and 0xF)
        flagC = result shr 8 and 0xFF != 0
    }

    private fun call(flag: Boolean) {
        if (flag) {
            push(PC + 2)
            PC = fetchWord()
            cycles += CpuCycles.ControlFlowCycles.CALL
        } else {
            PC += 2
        }
    }

    private fun push(word: Int) {
        bus.writeByte(--SP, word.hi())
        bus.writeByte(--SP, word.lo())
    }

    private fun pop(): Int {
        val lo = bus.readByte(SP++)
        val hi = bus.readByte(SP++)
        val value = hi shl 8 or lo
        return value
    }

    // CB Instructions
    private fun rlc(value: Int): Int {
        val result = ((value shl 1) or (value shr 7))
        F = 0
        flagZ = result and 0xFF == 0
        //flagN = false
        //flagH = false
        flagC = (value and 0x80) != 0
        return result
    }

    private fun rrc(value: Int): Int {
        val result = ((value shr 1) or (value shl 7))
        F = 0
        flagZ = result and 0xFF == 0
        //flagN = false
        //flagH = false
        flagC = (value and 0x1) != 0
        return result
    }

    private fun rl(value: Int): Int {
        val prevC = if (flagC) 0x1 else 0
        val result = value shl 1 or prevC
        F = 0
        flagZ = result and 0xFF == 0
        //flagN = false
        //flagH = false
        flagC = (value and 0x80) != 0
        return result
    }

    private fun rr(value: Int): Int {
        val prevC = if (flagC) 0x80 else 0
        val result = value shr 1 or prevC
        F = 0
        flagZ = result and 0xFF == 0
        //flagN = false
        //flagH = false
        flagC = (value and 0x1) != 0
        return result
    }

    private fun sla(value: Int): Int {
        val result = value shl 1
        F = 0
        flagZ = result and 0xFF == 0
        //flagN = false
        //flagH = false
        flagC = (value and 0x80) != 0
        return result
    }

    private fun sra(value: Int): Int {
        val result = value shr 1 or (value and 0x80)
        F = 0
        flagZ = result and 0xFF == 0
        //flagN = false
        //flagH = false
        flagC = (value and 0x1) != 0
        return result
    }

    private fun swap(value: Int): Int {
        val result = ((value and 0xF0) shr 4) or ((value and 0x0F) shl 4)
        F = 0
        flagZ = result and 0xFF == 0
        //flagN = false
        //flagH = false
        //flagC = false
        return result
    }

    private fun srl(value: Int): Int {
        val result = value shr 1
        F = 0
        flagZ = result and 0xFF == 0
        //flagN = false
        //flagH = false
        flagC = (value and 0x1) != 0
        return result
    }

    private fun bit(b: Int, value: Int) {
        flagZ = value and b == 0
        flagN = false
        flagH = true
    }

    private fun res(b: Int, value: Int): Int {
        return value and b.inv()
    }

    private fun set(b: Int, value: Int): Int {
        return value or b
    }

    private val byteLengthFormat = HexFormat {
        number.removeLeadingZeros = true
        number.minLength = 2
        upperCase = true
    }
    private val shortLengthFormat = HexFormat {
        number.removeLeadingZeros = true
        number.minLength = 4
        upperCase = true
    }

    private fun generateInstructionLog(): String {
        //A: 00 F: 00 B: 00 C: 00 D: 00 E: 00 H: 00 L: 00 SP: 0000 PC: 00:0000 (31 FE FF AF)
        return "A: ${A.toHexString(byteLengthFormat)} F: ${F.toHexString(byteLengthFormat)} B: ${B.toHexString(byteLengthFormat)} C: ${C.toHexString(byteLengthFormat)} D: ${D.toHexString(byteLengthFormat)}" +
                " E: ${E.toHexString(byteLengthFormat)} H: ${H.toHexString(byteLengthFormat)} L: ${L.toHexString(byteLengthFormat)} SP: ${SP.toHexString(shortLengthFormat)}" +
                " PC: 00:${PC.toHexString(shortLengthFormat)} (${bus.readByte(PC + 0).toHexString(byteLengthFormat)} ${bus.readByte(PC + 1).toHexString(byteLengthFormat)} ${bus.readByte(PC + 2).toHexString(byteLengthFormat)} ${bus.readByte(PC + 3).toHexString(byteLengthFormat)})" +
                " TIMA: ${bus.TIMA.toHexString(byteLengthFormat)} IF: ${bus.interruptFlags.toHexString(byteLengthFormat)} DIV: ${bus.DIV.toHexString(byteLengthFormat)} divCounter:" // ${timer.divCounter} timerCounter: ${timer.timerCounter}
    }

}