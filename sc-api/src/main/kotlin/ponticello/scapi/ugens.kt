package ponticello.scapi

import ponticello.scsynth.Rate

sealed class UGen {
    abstract val rate: Rate
}

data class ConstantUGen(val value: Float) : UGen() {
    override val rate: Rate
        get() = Rate.Scalar

    override fun toString(): String = value.toString()
}

val Float.c get() = ConstantUGen(this)
val Int.c get() = this.toFloat().c
val Double.c get() = this.toFloat().c

val List<Float>.c: UGen @JvmName("floatConstants") get() = MultiChannelUGen(map { it.c })
val List<Int>.c: UGen @JvmName("intConstants") get() = MultiChannelUGen(map { it.c })
val List<Double>.c: UGen @JvmName("doubleConstants") get() = MultiChannelUGen(map { it.c })

fun c(vararg values: Float) = values.asList().c
fun c(vararg values: Int) = values.asList().c
fun c(vararg values: Double) = values.asList().c

data class RegularUGen(
    val className: String, override val rate: Rate,
    val inputs: List<UGen>, val outputRates: List<Rate>
) : UGen() {
    override fun toString(): String = "$className.${rate.code}($inputs)"
}

enum class BinaryOperator { ADD, SUB, MUL, DIV }

enum class UnaryOperator { UNARY_MINUS, ABS, SIN, COS, TAN, EXP, LOG, POW }

data class BinaryOpUGen(val operator: BinaryOperator, val left: UGen, val right: UGen) : UGen() {
    override val rate: Rate
        get() = maxOf(left.rate, right.rate)

    override fun toString(): String = "($left $operator $right)"
}

data class UnaryOpUgen(val operator: UnaryOperator, val input: UGen) : UGen() {
    override val rate: Rate
        get() = input.rate

    override fun toString(): String = "($operator $input)"
}

data class ControlUGen(val parameterName: String, override val rate: Rate, val defaultValues: List<Float>) : UGen() {
    override fun toString(): String = "$parameterName.${rate.code}($defaultValues)"
}

data class MultiChannelUGen(val channels: List<UGen>) : UGen() {
    override val rate: Rate
        get() = channels.maxOf { ch -> ch.rate }

    override fun toString(): String = channels.toString()
}

data class OutputProxy(val source: UGen, val index: Int) : UGen() {
    override val rate: Rate
        get() = source.rate

    override fun toString(): String = "$source[$index]"
}

infix fun UGen.x(n: Int) = MultiChannelUGen(List(n) { this })

operator fun UGen.plus(other: UGen) = binOp(this, other, BinaryOperator.ADD)

operator fun UGen.minus(other: UGen) = binOp(this, other, BinaryOperator.SUB)

operator fun UGen.times(other: UGen) = binOp(this, other, BinaryOperator.MUL)

operator fun UGen.div(other: UGen) = binOp(this, other, BinaryOperator.DIV)

operator fun UGen.unaryMinus() = unaryOp(this, UnaryOperator.UNARY_MINUS)

fun UGen.abs() = unaryOp(this, UnaryOperator.ABS)

val UGen.sum: UGen
    get() = when (this) {
        is MultiChannelUGen -> when (channels.size) {
            0 -> 0f.c
            1 -> channels[0]
            else -> channels.reduce(UGen::plus)
        }

        else -> this
    }

private fun createControlUGen(parameterName: String, rate: Rate, defaults: FloatArray): UGen {
    val ugen = ControlUGen(parameterName, rate, defaults.asList())
    return when (defaults.size) {
        0 -> error("Control parameter $parameterName must have at least one default value")
        1 -> OutputProxy(ugen, 0)
        else -> MultiChannelUGen(defaults.indices.map { idx -> OutputProxy(ugen, idx) })
    }
}

fun String.kr(vararg defaults: Float) = createControlUGen(this, Rate.Control, defaults)

fun String.ar(vararg defaults: Float) = createControlUGen(this, Rate.Audio, defaults)

fun String.ir(vararg defaults: Float) = createControlUGen(this, Rate.Control, defaults)

val String.kr get() = kr(0f)
val String.ar get() = ar(0f)
val String.ir get() = ir(0f)

private inline fun expand1(uGen: UGen, op: (UGen) -> UGen) = when (uGen) {
    is MultiChannelUGen -> MultiChannelUGen(uGen.channels.map(op))
    else -> op(uGen)
}

private fun binOp(left: UGen, right: UGen, operator: BinaryOperator) =
    expand2(left, right) { l, r -> BinaryOpUGen(operator, l, r) }

private fun unaryOp(input: UGen, operator: UnaryOperator) =
    expand1(input) { UnaryOpUgen(operator, it) }

private inline fun zip2(l1: List<UGen>, l2: List<UGen>, op: (UGen, UGen) -> UGen): List<UGen> = buildList {
    for (i in 0 until maxOf(l1.size, l2.size)) {
        val u1 = l1[i % l1.size]
        val u2 = l2[i % l2.size]
        add(op(u1, u2))
    }
}

private inline fun zip3(l1: List<UGen>, l2: List<UGen>, l3: List<UGen>, op: (UGen, UGen, UGen) -> UGen): List<UGen> =
    buildList {
        for (i in 0 until maxOf(l1.size, l2.size, l3.size)) {
            val u1 = l1[i % l1.size]
            val u2 = l2[i % l2.size]
            val u3 = l3[i % l3.size]
            add(op(u1, u2, u3))
        }
    }

private inline fun expand2(u1: UGen, u2: UGen, op: (UGen, UGen) -> UGen) = when {
    u1 is MultiChannelUGen && u2 is MultiChannelUGen -> MultiChannelUGen(zip2(u1.channels, u2.channels, op))
    u1 is MultiChannelUGen -> MultiChannelUGen(u1.channels.map { op(it, u2) })
    u2 is MultiChannelUGen -> MultiChannelUGen(u2.channels.map { op(u1, it) })
    else -> op(u1, u2)
}

private inline fun expand3(u1: UGen, u2: UGen, u3: UGen, crossinline op: (UGen, UGen, UGen) -> UGen): UGen {
    val mc1 = u1 is MultiChannelUGen
    val mc2 = u2 is MultiChannelUGen
    val mc3 = u3 is MultiChannelUGen
    return when {
        mc1 && mc2 && mc3 -> MultiChannelUGen(zip3(u1.channels, u2.channels, u3.channels, op))
        mc1 && mc2 -> MultiChannelUGen(zip2(u1.channels, u2.channels) { x, y -> op(x, y, y) })
        mc1 && mc3 -> MultiChannelUGen(zip2(u1.channels, u3.channels) { x, y -> op(x, u2, y) })
        mc2 && mc3 -> MultiChannelUGen(zip2(u2.channels, u3.channels) { x, y -> op(u1, x, y) })
        mc1 -> MultiChannelUGen(u1.channels.map { op(it, u2, u3) })
        mc2 -> MultiChannelUGen(u2.channels.map { op(u1, it, u3) })
        mc3 -> MultiChannelUGen(u3.channels.map { op(u1, u2, it) })
        else -> op(u1, u2, u3)
    }
}

private fun expand2(u1: UGen, u2: UGen, className: String, outputRates: List<Rate>) =
    expand2(u1, u2) { x, y -> RegularUGen(className, Rate.Audio, listOf(x, y), outputRates) }

private inline fun expandN(list: List<UGen>, op: (List<UGen>) -> UGen): UGen {
    val maxSize = list.maxOf { u -> if (u is MultiChannelUGen) u.channels.size else 1 }
    return MultiChannelUGen(List(maxSize) { i ->
        val components = list.map { u ->
            if (u is MultiChannelUGen) u.channels[i % u.channels.size] else u
        }
        op(components)
    })
}

private fun createRegularUgen(
    className: String, rate: Rate, inputs: Array<out UGen>, outputs: Int
): UGen = expandN(inputs.asList()) { ugens ->
    val ugen = RegularUGen(className, rate, ugens, List(outputs) { rate })
    if (outputs == 1) ugen
    else MultiChannelUGen(List(outputs) { idx -> OutputProxy(ugen, idx) })
}

private fun kr(className: String, vararg inputs: UGen, outputs: Int = 1) =
    createRegularUgen(className, Rate.Control, inputs, outputs)

private fun ar(className: String, vararg inputs: UGen, outputs: Int = 1) =
    createRegularUgen(className, Rate.Audio, inputs, outputs)

object SinOsc {
    fun kr(
        freq: UGen = 440f.c, phase: UGen = 0f.c, mul: UGen = 1f.c
    ) = kr("SinOsc", freq, phase, mul)

    fun ar(
        freq: UGen = 440f.c, phase: UGen = 0f.c, mul: UGen = 1f.c
    ) = ar("SinOsc", freq, phase, mul)
}

object Pan2 {
    fun kr(input: UGen, pos: UGen = 0f.c, mul: UGen = 1f.c) = kr("Pan2", input, pos, mul)

    fun ar(input: UGen, pos: UGen = 0f.c, mul: UGen = 1f.c) = ar("Pan2", input, pos, mul)
}

object LFSaw {
    fun kr(
        freq: UGen = 440f.c, phase: UGen = 0f.c, mul: UGen = 1f.c
    ) = kr("LFSaw", freq, phase, mul)

    fun ar(
        freq: UGen = 440f.c, phase: UGen = 0f.c, mul: UGen = 1f.c
    ) = ar("LFSaw", freq, phase, mul)
}

object Out {
    fun ar(bus: Int = 0, snd: UGen) = ar("Out", bus.c, snd)

    fun kr(bus: Int, snd: UGen) = kr("Out", bus.c, snd)
}

object In {
    fun ar(bus: Int = 0, channels: Int) = ar("In", bus.c, outputs = channels)
    fun kr(bus: Int, channels: Int) = kr("In", bus.c, outputs = channels)
}