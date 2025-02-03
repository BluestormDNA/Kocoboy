@file:OptIn(ExperimentalStdlibApi::class)

package io.github.bluestormdna.kocoboy.core

import kotlin.experimental.and
import kotlin.reflect.KMutableProperty0


@OptIn(ExperimentalStdlibApi::class)
class CPU(private val bus: Bus) {

    private var A: Int = 0
        set(value) {
            field = value and 0xFF
        }
    private var F: Int = 0
        set(value) {
            field = value and 0xFF
        }

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
        get() {
            return F and 0x80 != 0
        }
        set(value) {
            F = if (value) F or 0x80 else F and 0x80.inv()
        }

    private var flagN: Boolean
        get() {
            return F and 0x40 != 0
        }
        set(value) {
            F = if (value) F or 0x40 else F and 0x40.inv()
        }

    private var flagH: Boolean
        get() {
            return F and 0x20 != 0
        }
        set(value) {
            F = if (value) F or 0x20 else F and 0x20.inv()
        }

    private var flagC: Boolean
        get() {
            return F and 0x10 != 0
        }
        set(value) {
            F = if (value) F or 0x10 else F and 0x10.inv()
        }

    private var AF: Int
        get() {
            return A shl 8 or F
        }
        set(value) {
            A = value shr 8 and 0xFF
            F = value and 0xF0
        }

    private var BC: Int
        get() {
            return B shl 8 or C
        }
        set(value) {
            B = value shr 8 and 0xFF
            C = value and 0xFF
        }

    private var DE: Int
        get() {
            return D shl 8 or E
        }
        set(value) {
            D = value shr 8 and 0xFF
            E = value and 0xFF
        }

    private var HL: Int
        get() {
            return H shl 8 or L
        }
        set(value) {
            H = value shr 8 and 0xFF
            L = value and 0xFF
        }

    private var M: Int
        get() {
            return bus.readByte(HL)
        }
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

    private fun Int.hi() = this shr 8 and 0xFF

    private fun Int.lo() = this and 0xFF

    private val r = arrayOf(::B, ::C, ::D, ::E, ::H, ::L, ::M, ::A)

    private val rp = arrayOf(::BC, ::DE, ::HL, ::SP)

    private val rpPushPop = arrayOf(::BC, ::DE, ::HL, ::AF)

    private val aluInstructions = arrayOf(::add, ::adc, ::sub, ::sbc, ::and, ::xor, ::or, ::cp)

    private val cbSRInstructions = arrayOf(::rlc, ::rrc, ::rl, ::rr, ::sla, ::sra, ::swap, ::srl)

    private val cbBInstructions = arrayOf(::unreachableOp, ::bit, ::res, ::set)

    private var cycles = 0

    private inline fun fetch(): Int {
        return bus.readByte(PC++)
    }

    private var debug = false

    @OptIn(ExperimentalStdlibApi::class)
    fun execute(): Int {
        //if (debug) {
        //    //val line = generateInstructionLog()
        //    //println(line)
        //}

        cycles = 0

        val opcode = fetch()
        val decoder = Decoder(opcode)

        when (opcode) {
            0x00 -> { /* nop */ }
            0x07 -> rlca()
            0x0F -> rrca()
            0x10 -> stop()
            0x17 -> rla()
            0x1F -> rra()
            0x27 -> daa()
            0x2F -> cpl()
            0x37 -> scf()
            0x3F -> ccf()
            0x18 -> jr(true)
            0x20 -> jr(!flagZ)
            0x28 -> jr(flagZ)
            0x30 -> jr(!flagC)
            0x38 -> jr(flagC)
            0x01, 0x11, 0x21, 0x31 -> {
                val register = rp[decoder.pair]
                loadReg16Imm(register)
            }

            0x08 -> {
                val lo = fetch()
                val hi = fetch()
                val imm16 = hi shl 8 or lo
                bus.writeByte(imm16, SP and 0xFF)
                bus.writeByte(imm16 + 1, SP shr 8 and 0xFF)
            }

            0x02 -> loadMemReg16A(BC)
            0x12 -> loadMemReg16A(DE)
            0x22 -> loadMemReg16A(HL++)
            0x32 -> loadMemReg16A(HL--)
            0x0A -> loadAMemReg16(BC)
            0x1A -> loadAMemReg16(DE)
            0x2A -> loadAMemReg16(HL++)
            0x3A -> loadAMemReg16(HL--)
            0x03, 0x13, 0x23, 0x33 -> {
                val register = rp[decoder.pair]
                incReg16(register)
            }

            0x04, 0x0C, 0x14, 0x1C, 0x24, 0x2C, 0x34, 0x3C -> {
                val register = r[decoder.to]
                incReg8(register)
            }

            0x05, 0x0D, 0x15, 0x1D, 0x25, 0x2D, 0x35, 0x3D -> {
                val register = r[decoder.to]
                decReg8(register)
            }

            0x0B, 0x1B, 0x2B, 0x3B -> {
                val register = rp[decoder.pair]
                decReg16(register)
            }

            0x06, 0x0E, 0x16, 0x1E, 0x26, 0x2E, 0x36, 0x3E -> {
                val register = r[decoder.to]
                loadReg8Imm(register)
            }

            0x09, 0x19, 0x29, 0x39 -> {
                val register = rp[decoder.pair]
                addHLReg(register)
            }

            0x76 -> halt()
            in 0x40..0x7F -> { // LD
                loadRegReg(r[decoder.to], r[decoder.from])
            }

            in 0x80..0xBF -> {
                val aluInstruction = aluInstructions[decoder.aluOpIndex]
                val register = r[decoder.from]
                val value = register.get()
                aluInstruction(value)
            }

            0xCB -> {
                val cbOpcode = fetch()
                val cbDecoder = Decoder(cbOpcode)
                if (cbOpcode < 0x40) {
                    val instruction = cbSRInstructions[cbDecoder.rotOpIndex]
                    val register = r[cbDecoder.from]
                    instruction(register)
                } else {
                    val instruction = cbBInstructions[cbDecoder.bitOpIndex]
                    val register = r[cbDecoder.from]
                    instruction(cbDecoder.bitOpBit, register)
                }
                cycles += CpuCycles.opcodeCBCycles[cbOpcode]
            }

            0xC0 -> ret(!flagZ)
            0xC8 -> ret(flagZ)
            0xC9 -> pop(::PC) // ret(true) Unconditional return shortcut as ret true adds cycles
            0xD0 -> ret(!flagC)
            0xD8 -> ret(flagC)
            0xD9 -> {
                pop(::PC) // ret(true) Unconditional return shortcut as ret true adds cycles
                ime = true
            }

            0xC1, 0xD1, 0xE1, 0xF1 -> {
                val register = rpPushPop[decoder.pair]
                pop(register)
            }

            0xC2 -> jp(!flagZ)
            0xC3 -> jp(true) // PC = imm16
            0xCA -> jp(flagZ)
            0xD2 -> jp(!flagC)
            0xDA -> jp(flagC)
            0xE9 -> PC = HL // Direct Jump

            0xC5, 0xD5, 0xE5, 0xF5 -> {
                val register = rpPushPop[decoder.pair]
                val value = register.get()
                push(value)
            }

            0xC6 -> add(fetch())
            0xCE -> adc(fetch())
            0xD6 -> sub(fetch())
            0xDE -> sbc(fetch())
            0xE6 -> and(fetch())
            0xEE -> xor(fetch())
            0xF6 -> or(fetch())
            0xFE -> cp(fetch())

            0xC7, 0xCF, 0xD7, 0xDF, 0xE7, 0xEF, 0xF7, 0xFF -> rst(decoder.rstAddress)

            0xE0 -> {
                val value = fetch()
                bus.writeByte(0xFF00 + value, A)
            }

            0xE2 -> bus.writeByte(0xFF00 + C, A)
            0xE8 -> {
                val value = fetch()
                SP = addSigned8(SP, value)
            }
            0xF0 -> {
                val value = fetch()
                //println("value read for 0xF0 at 0xFF00 + ${value.toHexString()} IF is ${mmu.interruptFlags.toHexString()}")
                //println("address: ${0xFF00 + value}")
                A = bus.readByte(0xFF00 + value)
                //println("A was set ${A.toHexString()}")
            }


            0xF2 -> A = bus.readByte(0xFF00 + C)
            0xF3 -> di()
            0xFB -> ei()
            0xC4 -> call(!flagZ)
            0xCC -> call(flagZ)
            0xCD -> call(true)
            0xD4 -> call(!flagC)
            0xDC -> call(flagC)
            0xEA -> {
                val lo = fetch()
                val hi = fetch()
                val imm16 = hi shl 8 or lo
                bus.writeByte(imm16, A)
            }

            0xFA -> {
                val lo = fetch()
                val hi = fetch()
                val imm16 = hi shl 8 or lo
                A = bus.readByte(imm16)
            }

            0xF8 -> {
                val value = fetch()
                HL = addSigned8(SP, value)
            }

            0xF9 -> {
                SP = HL
            }

            0xD3, 0xDB, 0xDD, 0xE3, 0xE4, 0xEB, 0xEC, 0xED, 0xF4, 0xFC, 0xFD -> {
                println("Unsupported operation ${opcode.toHexString()}")
            }

            else -> throw IllegalStateException("Unknown Opcode: ${opcode.toHexString()}")
        }

        cycles += CpuCycles.opcodeCycles[opcode]
        return cycles
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
        flagZ = false
        flagN = false
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
            val lo = fetch()
            val hi = fetch()
            val imm16 = hi shl 8 or lo
            PC = imm16
            cycles += CpuCycles.ControlFlowCycles.JP
        } else {
            PC += 2
        }
    }

    private fun ret(flag: Boolean) {
        if (flag) {
            pop(::PC)
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

    private fun loadAMemReg16(addr: Int) {
        A = bus.readByte(addr)
    }

    private fun loadMemReg16A(addr: Int) {
        bus.writeByte(addr, A)
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

    private fun addHLReg(fromRef: KMutableProperty0<Int>) {
        val from = fromRef.get()
        val result = HL + from
        flagN = false
        flagH = ((HL and 0xFFF) + (from and 0xFFF)) > 0xFFF
        flagC = result shr 16 and 0xFFFF != 0
        HL = result
    }

    private fun loadReg8Imm(toRef: KMutableProperty0<Int>) {
        val value = fetch()
        toRef.set(value)
    }

    private fun loadReg16Imm(toRef: KMutableProperty0<Int>) {
        val lo = fetch()
        val hi = fetch()
        toRef.set(hi shl 8 or lo)
    }

    private fun incReg8(toRef: KMutableProperty0<Int>) {
        val to = toRef.get()
        val value = to + 1
        flagZ = value and 0xFF == 0
        flagN = false
        flagH = ((to and 0xF) + (1 and 0xF)) > 0xF
        toRef.set(value)
    }

    private fun incReg16(toRef: KMutableProperty0<Int>) {
        val value = toRef.get() + 1
        toRef.set(value)
    }

    private fun decReg8(toRef: KMutableProperty0<Int>) {
        val to = toRef.get()
        val value = to - 1
        flagZ = value and 0xFF == 0
        flagN = true
        flagH = (to and 0xF) < (1 and 0xF)
        toRef.set(value)
    }

    private fun decReg16(toRef: KMutableProperty0<Int>) {
        val value = toRef.get() - 1
        toRef.set(value)
    }

    // Instructions
    private fun stop() {
        println("Opcode: STOP")
    }

    private fun loadRegReg(toRef: KMutableProperty0<Int>, fromRef: KMutableProperty0<Int>) {
        toRef.set(fromRef.get())
    }

    private fun add(value: Int) {
        val result = A + value
        flagZ = result and 0xFF == 0
        flagN = false
        flagH = ((A and 0xF) + (value and 0xF)) > 0xF
        flagC = result shr 8 and 0xFF != 0
        A = result
    }

    private fun adc(value: Int) {
        val carry = if (flagC) 1 else 0
        val result = A + value + carry
        flagZ = result and 0xFF == 0
        flagN = false
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
        flagZ = result and 0xFF == 0
        flagN = false
        flagH = true
        flagC = false
        A = result
    }

    private fun xor(value: Int) {
        val result = A xor value
        flagZ = result and 0xFF == 0
        flagN = false
        flagH = false
        flagC = false
        A = result
    }

    private fun or(value: Int) {
        val result = A or value
        flagZ = result and 0xFF == 0
        flagN = false
        flagH = false
        flagC = false
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
            val pcLo = fetch()
            val pcHi = fetch()
            PC = pcHi shl 8 or pcLo
            cycles += CpuCycles.ControlFlowCycles.CALL
        } else {
            PC += 2
        }
    }

    private fun push(word: Int) {
        bus.writeByte(--SP, word.hi())
        bus.writeByte(--SP, word.lo())
    }

    private fun pop(toRef: KMutableProperty0<Int>) {
        val lo = bus.readByte(SP++)
        val hi = bus.readByte(SP++)
        val value = hi shl 8 or lo
        toRef.set(value)
    }

    // CB Instructions
    private fun rlc(fromRef: KMutableProperty0<Int>) {
        val value = fromRef.get()
        val result = ((value shl 1) or (value shr 7))
        flagZ = result and 0xFF == 0
        flagN = false
        flagH = false
        flagC = (value and 0x80) != 0
        fromRef.set(result)
    }

    private fun rrc(fromRef: KMutableProperty0<Int>) {
        val value = fromRef.get()
        val result = ((value shr 1) or (value shl 7))
        flagZ = result and 0xFF == 0
        flagN = false
        flagH = false
        flagC = (value and 0x1) != 0
        fromRef.set(result)
    }

    private fun rl(fromRef: KMutableProperty0<Int>) {
        val prevC = if (flagC) 0x1 else 0
        val value = fromRef.get()
        val result = value shl 1 or prevC
        flagZ = result and 0xFF == 0
        flagN = false
        flagH = false
        flagC = (value and 0x80) != 0
        fromRef.set(result)
    }

    private fun rr(fromRef: KMutableProperty0<Int>) {
        val prevC = if (flagC) 0x80 else 0
        val value = fromRef.get()
        val result = value shr 1 or prevC
        flagZ = result and 0xFF == 0
        flagN = false
        flagH = false
        flagC = (value and 0x1) != 0
        fromRef.set(result)
    }

    private fun sla(fromRef: KMutableProperty0<Int>) {
        val value = fromRef.get()
        val result = value shl 1
        flagZ = result and 0xFF == 0
        flagN = false
        flagH = false
        flagC = (value and 0x80) != 0
        fromRef.set(result)
    }

    private fun sra(fromRef: KMutableProperty0<Int>) {
        val value = fromRef.get()
        val result = value shr 1 or (value and 0x80)
        flagZ = result and 0xFF == 0
        flagN = false
        flagH = false
        flagC = (value and 0x1) != 0
        fromRef.set(result)
    }

    private fun swap(fromRef: KMutableProperty0<Int>) {
        val value = fromRef.get()
        val result = ((value and 0xF0) shr 4) or ((value and 0x0F) shl 4)
        flagZ = result and 0xFF == 0
        flagN = false
        flagH = false
        flagC = false
        fromRef.set(result)
    }

    private fun srl(fromRef: KMutableProperty0<Int>) {
        val value = fromRef.get()
        val result = value shr 1
        flagZ = result and 0xFF == 0
        flagN = false
        flagH = false
        flagC = (value and 0x1) != 0
        fromRef.set(result)
    }

    private fun unreachableOp(b: Int, fromRef: KMutableProperty0<Int>) {
        throw IllegalArgumentException("Unreachable bit op")
    }

    private fun bit(b: Int, fromRef: KMutableProperty0<Int>) {
        val value = fromRef.get()
        flagZ = value and (1 shl b) == 0
        flagN = false
        flagH = true
    }

    private fun res(b: Int, fromRef: KMutableProperty0<Int>) {
        val from = fromRef.get()
        val value = from and (1 shl b).inv()
        fromRef.set(value)
    }

    private fun set(b: Int, fromRef: KMutableProperty0<Int>) {
        val from = fromRef.get()
        val value = from or (1 shl b)
        fromRef.set(value)
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

    private fun generateInstructionLog() : String {
        //A: 00 F: 00 B: 00 C: 00 D: 00 E: 00 H: 00 L: 00 SP: 0000 PC: 00:0000 (31 FE FF AF)
        return "A: ${A.toHexString(byteLengthFormat)} F: ${F.toHexString(byteLengthFormat)} B: ${B.toHexString(byteLengthFormat)} C: ${C.toHexString(byteLengthFormat)} D: ${D.toHexString(byteLengthFormat)}" +
                " E: ${E.toHexString(byteLengthFormat)} H: ${H.toHexString(byteLengthFormat)} L: ${L.toHexString(byteLengthFormat)} SP: ${SP.toHexString(shortLengthFormat)}" +
                " PC: 00:${PC.toHexString(shortLengthFormat)} (${bus.readByte(PC + 0).toHexString(byteLengthFormat)} ${bus.readByte(PC + 1).toHexString(byteLengthFormat)} ${bus.readByte(PC + 2).toHexString(byteLengthFormat)} ${bus.readByte(PC + 3).toHexString(byteLengthFormat)})" +
                " TIMA: ${bus.TIMA.toHexString(byteLengthFormat)} IF: ${bus.interruptFlags.toHexString(byteLengthFormat)} DIV: ${bus.DIV.toHexString(byteLengthFormat)} divCounter:" // ${timer.divCounter} timerCounter: ${timer.timerCounter}
    }

}