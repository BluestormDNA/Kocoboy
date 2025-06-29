package io.github.bluestormdna.kocoboy.core


@OptIn(ExperimentalStdlibApi::class)
class CPU(private val bus: Bus) {

    private var A: UByte = 0u

    private var F: UByte = 0u

    private var B: UByte = 0u
    private var C: UByte = 0u

    private var D: UByte = 0u
    private var E: UByte = 0u

    private var H: UByte = 0u
    private var L: UByte = 0u

    private val zMask = 0x80.toUByte()
    private val nMask = 0x40.toUByte()
    private val hMask = 0x20.toUByte()
    private val cMask = 0x10.toUByte()
    private val zero = 0.toUByte()

    private var flagZ: Boolean
        get() = F and zMask != zero
        set(value) {
            F = if (value) F or zMask else F and zMask.inv()
        }

    private var flagN: Boolean
        get() = F and nMask != zero
        set(value) {
            F = if (value) F or nMask else F and nMask.inv()
        }

    private var flagH: Boolean
        get() = F and hMask != zero
        set(value) {
            F = if (value) F or hMask else F and hMask.inv()
        }

    private var flagC: Boolean
        get() = F and cMask != zero
        set(value) {
            F = if (value) F or cMask else F and cMask.inv()
        }

    private var AF: UShort
        get() = (A.toUInt() shl 8 or F.toUInt()).toUShort()
        set(value) {
            A = (value.toUInt() shr 8).toUByte()
            F = (value and 0xF0u).toUByte()
        }

    private var BC: UShort
        get() = (B.toUInt() shl 8 or C.toUInt()).toUShort()
        set(value) {
            B = (value.toUInt() shr 8).toUByte()
            C = value.toUByte()
        }

    private var DE: UShort
        get() = (D.toUInt() shl 8 or E.toUInt()).toUShort()
        set(value) {
            D = (value.toUInt() shr 8).toUByte()
            E = value.toUByte()
        }

    private var HL: UShort
        get() = (H.toUInt() shl 8 or L.toUInt()).toUShort()
        set(value) {
            H = (value.toUInt() shr 8).toUByte()
            L = value.toUByte()
        }

    private var M: UByte
        get() = bus.readByte(HL)
        set(value) {
            bus.writeByte(HL, value)
        }

    private var PC: UShort = 0u
    private var SP: UShort = 0u

    init {
        reset()
    }

    fun reset() {
        AF = 0x01B0u
        BC = 0x0013u
        DE = 0x00D8u
        HL = 0x014Du
        SP = 0xFFFEu
        PC = 0x100u
        ime = false
        imeEnabler = false
        halted = false
        haltBug = false
    }

    private var ime: Boolean = false
    private var imeEnabler: Boolean = false
    private var halted: Boolean = false
    private var haltBug: Boolean = false

    private var cycles = 0

    private inline fun fetch(): UByte {
        return bus.readByte(PC++)
    }

    private var debug = false

    fun execute(): Int {
        //if (debug) {
        //    //val line = generateInstructionLog()
        //    //println(line)
        //}

        cycles = 0

        val opcode = fetch().toInt()

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
                bus.writeByte(imm16, SP.toUByte())
                bus.writeByte((imm16 + 1u).toUShort(), (SP.toUInt() shr 8).toUByte())
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
            0x2A -> A = bus.readByte((HL++))
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

            0xE0 -> bus.writeByte((0xFF00u + fetch()).toUShort(), A)
            0xE1 -> HL = pop()
            0xE2 -> bus.writeByte((0xFF00u + C).toUShort(), A)
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

            0xF0 -> A = bus.readByte((0xFF00u + fetch()).toUShort())
            0xF1 -> AF = pop()
            0xF2 -> A = bus.readByte((0xFF00u + C).toUShort())
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
        val opcode = fetch().toInt()

        when (opcode) {
            0x00 -> B = rlc(B.toInt()).toUByte()
            0x01 -> C = rlc(C.toInt()).toUByte()
            0x02 -> D = rlc(D.toInt()).toUByte()
            0x03 -> E = rlc(E.toInt()).toUByte()
            0x04 -> H = rlc(H.toInt()).toUByte()
            0x05 -> L = rlc(L.toInt()).toUByte()
            0x06 -> M = rlc(M.toInt()).toUByte()
            0x07 -> A = rlc(A.toInt()).toUByte()

            0x08 -> B = rrc(B.toInt()).toUByte()
            0x09 -> C = rrc(C.toInt()).toUByte()
            0x0A -> D = rrc(D.toInt()).toUByte()
            0x0B -> E = rrc(E.toInt()).toUByte()
            0x0C -> H = rrc(H.toInt()).toUByte()
            0x0D -> L = rrc(L.toInt()).toUByte()
            0x0E -> M = rrc(M.toInt()).toUByte()
            0x0F -> A = rrc(A.toInt()).toUByte()

            0x10 -> B = rl(B.toInt()).toUByte()
            0x11 -> C = rl(C.toInt()).toUByte()
            0x12 -> D = rl(D.toInt()).toUByte()
            0x13 -> E = rl(E.toInt()).toUByte()
            0x14 -> H = rl(H.toInt()).toUByte()
            0x15 -> L = rl(L.toInt()).toUByte()
            0x16 -> M = rl(M.toInt()).toUByte()
            0x17 -> A = rl(A.toInt()).toUByte()

            0x18 -> B = rr(B.toInt()).toUByte()
            0x19 -> C = rr(C.toInt()).toUByte()
            0x1A -> D = rr(D.toInt()).toUByte()
            0x1B -> E = rr(E.toInt()).toUByte()
            0x1C -> H = rr(H.toInt()).toUByte()
            0x1D -> L = rr(L.toInt()).toUByte()
            0x1E -> M = rr(M.toInt()).toUByte()
            0x1F -> A = rr(A.toInt()).toUByte()

            0x20 -> B = sla(B.toInt()).toUByte()
            0x21 -> C = sla(C.toInt()).toUByte()
            0x22 -> D = sla(D.toInt()).toUByte()
            0x23 -> E = sla(E.toInt()).toUByte()
            0x24 -> H = sla(H.toInt()).toUByte()
            0x25 -> L = sla(L.toInt()).toUByte()
            0x26 -> M = sla(M.toInt()).toUByte()
            0x27 -> A = sla(A.toInt()).toUByte()

            0x28 -> B = sra(B.toInt()).toUByte()
            0x29 -> C = sra(C.toInt()).toUByte()
            0x2A -> D = sra(D.toInt()).toUByte()
            0x2B -> E = sra(E.toInt()).toUByte()
            0x2C -> H = sra(H.toInt()).toUByte()
            0x2D -> L = sra(L.toInt()).toUByte()
            0x2E -> M = sra(M.toInt()).toUByte()
            0x2F -> A = sra(A.toInt()).toUByte()

            0x30 -> B = swap(B.toInt()).toUByte()
            0x31 -> C = swap(C.toInt()).toUByte()
            0x32 -> D = swap(D.toInt()).toUByte()
            0x33 -> E = swap(E.toInt()).toUByte()
            0x34 -> H = swap(H.toInt()).toUByte()
            0x35 -> L = swap(L.toInt()).toUByte()
            0x36 -> M = swap(M.toInt()).toUByte()
            0x37 -> A = swap(A.toInt()).toUByte()

            0x38 -> B = srl(B.toInt()).toUByte()
            0x39 -> C = srl(C.toInt()).toUByte()
            0x3A -> D = srl(D.toInt()).toUByte()
            0x3B -> E = srl(E.toInt()).toUByte()
            0x3C -> H = srl(H.toInt()).toUByte()
            0x3D -> L = srl(L.toInt()).toUByte()
            0x3E -> M = srl(M.toInt()).toUByte()
            0x3F -> A = srl(A.toInt()).toUByte()

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
            if ((flags and 0x1Fu) == zero) {
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
            PC = (0x40 + (8 * b)).toUShort()
            ime = false
            bus.clearInterrupt(b)
        }
    }

    fun updateIme() {
        ime = ime or imeEnabler
        imeEnabler = false
    }

    private fun addSigned8(register: UShort, value: UByte): UShort {
        F = 0u
        //flagZ = false
        //flagN = false
        flagH = ((register and 0xFu) + (value and 0xFu)) > 0xFu
        flagC = ((register and 0xFFu) + value) shr 8 and 0xFFu != 0u
        return (register + value.toByte().toShort().toUShort()).toUShort()
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
                A = (A - 0x60u).toUByte()
            }
            if (flagH) {
                A = (A - 0x6u).toUByte()
            }
        } else { // add
            if (flagC || (A > 0x99u)) {
                A = (A + 0x60u).toUByte()
                flagC = true
            }
            if (flagH || (A and 0xFu) > 0x9u) {
                A = (A + 0x6u).toUByte()
            }
        }
        flagZ = A == zero
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
        PC = b.toUShort()
    }

    private fun jp(flag: Boolean) {
        if (flag) {
            PC = fetchWord()
            cycles += CpuCycles.ControlFlowCycles.JP
        } else {
            PC = (PC + 2u).toUShort()
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
        F = 0u
        flagC = A and 0x01u != zero
        A = (A shr 1.toUByte()) or (if (prevC) 0x80u else 0u)
    }

    private fun rla() {
        val prevC = flagC
        F = 0u
        flagC = A and 0x80u != zero
        A = (A shl 0x1) or (if (prevC) 0x1u else 0u)
    }

    private fun rrca() {
        F = 0u
        flagC = A and 0x01u != zero
        A = (A shr 0x1) or (A shl 7)
    }

    private fun rlca() {
        F = 0u
        flagC = A and 0x80u != zero
        A = (A shl 1) or (A shr 7)
    }

    private fun jr(flag: Boolean) {
        if (flag) {
            val rel = fetch().toByte().toUShort()
            PC = (PC + rel).toUShort()
            cycles += CpuCycles.ControlFlowCycles.JR
        } else {
            PC++
        }
    }

    private fun dad(value: UShort) {
        val result = HL + value
        flagN = false
        flagH = ((HL and 0xFFFu) + (value and 0xFFFu)) > 0xFFFu
        flagC = result shr 16 and 0xFFFFu != 0u
        HL = result.toUShort()
    }

    private fun fetchWord(): UShort {
        val lo = fetch().toUInt()
        val hi = fetch().toUInt()
        return (hi shl 8 or lo).toUShort()
    }

    private fun incReg8(value: UByte): UByte {
        val result = (value + 1u).toUByte()
        flagZ = result == zero
        flagN = false
        flagH = ((value and 0xFu) + (1u and 0xFu)) > 0xFu
        return result
    }

    private fun decReg8(value: UByte): UByte {
        val result = (value - 1u).toUByte()
        flagZ = result == zero
        flagN = true
        flagH = (value and 0xFu) < (1u and 0xFu)
        return result
    }

    private fun stop() {
        println("Opcode: STOP")
    }

    private fun add(value: UByte) {
        val result = A + value
        F = 0u
        flagZ = result and 0xFFu == 0u
        //flagN = false
        flagH = ((A.toUInt() and 0xFu) + (value and 0xFu)) > 0xFu
        flagC = result shr 8 and 0xFFu != 0u
        A = result.toUByte()
    }

    private fun adc(value: UByte) {
        val carry = if (flagC) 1u else 0u
        val result = A + value + carry
        F = 0u
        flagZ = result and 0xFFu == 0u
        //flagN = false
        flagH = ((A.toUInt() and 0xFu) + (value and 0xFu) + carry) > 0xFu
        flagC = result shr 8 and 0xFFu != 0u
        A = result.toUByte()
    }

    private fun sub(value: UByte) {
        val result = A - value
        flagZ = result and 0xFFu == 0u
        flagN = true
        flagH = (A and 0xFu) < (value and 0xFu)
        flagC = result shr 8 and 0xFFu != 0u
        A = result.toUByte()
    }

    private fun sbc(value: UByte) {
        val carry = if (flagC) 1u else 0u
        val result = A - value - carry
        flagZ = result and 0xFFu == 0u
        flagN = true
        flagH = (A and 0xFu) < (value and 0xFu) + carry
        flagC = result shr 8 and 0xFFu != 0u
        A = result.toUByte()
    }

    private fun and(value: UByte) {
        val result = A and value
        F = 0u
        flagZ = result and 0xFFu == zero
        //flagN = false
        flagH = true
        //flagC = false
        A = result
    }

    private fun xor(value: UByte) {
        val result = A xor value
        F = 0u
        flagZ = result == zero
        //flagN = false
        //flagH = false
        //flagC = false
        A = result
    }

    private fun or(value: UByte) {
        val result = A or value
        F = 0u
        flagZ = result == zero
        //flagN = false
        //flagH = false
        //flagC = false
        A = result
    }

    private fun cp(value: UByte) {
        val result = A.toUByte() - value
        flagZ = result and 0xFFu == 0u
        flagN = true
        flagH = (A.toUByte() and 0xFu) < (value and 0xFu)
        flagC = result shr 8 and 0xFFu != 0u
    }

    private fun call(flag: Boolean) {
        if (flag) {
            push((PC + 2u).toUShort())
            PC = fetchWord()
            cycles += CpuCycles.ControlFlowCycles.CALL
        } else {
            PC = (PC + 2u).toUShort()
        }
    }

    private fun push(word: UShort) {
        bus.writeByte(--SP, (word.toUInt() shr 8).toUByte())
        bus.writeByte(--SP, word.toUByte())
    }

    private fun pop(): UShort {
        val lo = bus.readByte(SP++).toUInt()
        val hi = bus.readByte(SP++).toUInt()
        val value = hi shl 8 or lo
        return value.toUShort()
    }

    // CB Instructions
    private fun rlc(value: Int): Int {
        val result = ((value shl 1) or (value shr 7))
        F = 0u
        flagZ = result and 0xFF == 0
        //flagN = false
        //flagH = false
        flagC = (value and 0x80) != 0
        return result
    }

    private fun rrc(value: Int): Int {
        val result = ((value shr 1) or (value shl 7))
        F = 0u
        flagZ = result and 0xFF == 0
        //flagN = false
        //flagH = false
        flagC = (value and 0x1) != 0
        return result
    }

    private fun rl(value: Int): Int {
        val prevC = if (flagC) 0x1 else 0
        val result = value shl 1 or prevC
        F = 0u
        flagZ = result and 0xFF == 0
        //flagN = false
        //flagH = false
        flagC = (value and 0x80) != 0
        return result
    }

    private fun rr(value: Int): Int {
        val prevC = if (flagC) 0x80 else 0
        val result = value shr 1 or prevC
        F = 0u
        flagZ = result and 0xFF == 0
        //flagN = false
        //flagH = false
        flagC = (value and 0x1) != 0
        return result
    }

    private fun sla(value: Int): Int {
        val result = value shl 1
        F = 0u
        flagZ = result and 0xFF == 0
        //flagN = false
        //flagH = false
        flagC = (value and 0x80) != 0
        return result
    }

    private fun sra(value: Int): Int {
        val result = value shr 1 or (value and 0x80)
        F = 0u
        flagZ = result and 0xFF == 0
        //flagN = false
        //flagH = false
        flagC = (value and 0x1) != 0
        return result
    }

    private fun swap(value: Int): Int {
        val result = ((value and 0xF0) shr 4) or ((value and 0x0F) shl 4)
        F = 0u
        flagZ = result and 0xFF == 0
        //flagN = false
        //flagH = false
        //flagC = false
        return result
    }

    private fun srl(value: Int): Int {
        val result = value shr 1
        F = 0u
        flagZ = result and 0xFF == 0
        //flagN = false
        //flagH = false
        flagC = (value and 0x1) != 0
        return result
    }

    private fun bit(b: Int, value: UByte) {
        flagZ = value and b.toUByte() == zero
        flagN = false
        flagH = true
    }

    private fun res(b: Int, value: UByte): UByte {
        return value and b.toUByte().inv()
    }

    private fun set(b: Int, value: UByte): UByte {
        return value or b.toUByte()
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
                " PC: 00:${PC.toHexString(shortLengthFormat)} (${bus.readByte(PC).toHexString(byteLengthFormat)} ${bus.readByte((PC + 1u).toUShort()).toHexString(byteLengthFormat)} ${bus.readByte((PC + 2u).toUShort()).toHexString(byteLengthFormat)} ${bus.readByte((PC + 3u).toUShort()).toHexString(byteLengthFormat)})"
    }

}