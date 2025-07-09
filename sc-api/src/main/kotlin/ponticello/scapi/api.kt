package ponticello.scapi

import ponticello.scsynth.Rate

val Float.c get() = ConstantUGen(this)
val Int.c get() = this.toFloat().c
val Double.c get() = this.toFloat().c

val List<Float>.c: UGen @JvmName("floatConstants") get() = MultiChannelUGen(map { it.c })
val List<Int>.c: UGen @JvmName("intConstants") get() = MultiChannelUGen(map { it.c })
val List<Double>.c: UGen @JvmName("doubleConstants") get() = MultiChannelUGen(map { it.c })

fun c(vararg values: Float) = values.asList().c
fun c(vararg values: Int) = values.asList().c
fun c(vararg values: Double) = values.asList().c

fun String.kr(vararg defaults: Float) = createControlUGen(this, Rate.Control, defaults)

fun String.ar(vararg defaults: Float) = createControlUGen(this, Rate.Audio, defaults)

fun String.ir(vararg defaults: Float) = createControlUGen(this, Rate.Control, defaults)

val String.kr get() = kr(0f)
val String.ar get() = ar(0f)
val String.ir get() = ir(0f)

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

infix fun UGen.x(n: Int) = MultiChannelUGen(List(n) { this })

object SinOsc {
    fun kr(
        freq: UGen = 440f.c, phase: UGen = 0f.c, mul: UGen = 1f.c
    ) = kr("SinOsc", freq, phase, mul)

    fun ar(
        freq: UGen = 440f.c, phase: UGen = 0f.c, mul: UGen = 1f.c
    ) = ar("SinOsc", freq, phase, mul)
}

object Pan2 {
    fun kr(input: UGen, pos: UGen = 0f.c, mul: UGen = 1f.c) = kr("Pan2", input, pos, mul, outputs = 2)

    fun ar(input: UGen, pos: UGen = 0f.c, mul: UGen = 1f.c) = ar("Pan2", input, pos, mul, outputs = 2)
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
    fun ar(bus: Int = 0, snd: UGen) = when (snd) {
        is MultiChannelUGen -> ar("Out", bus.c, *snd.channels.toTypedArray(), outputs = snd.channels.size)
        else -> ar("Out", bus.c, snd, outputs = 1)
    }

    fun kr(bus: Int, snd: UGen) = when (snd) {
        is MultiChannelUGen -> kr("Out", bus.c, *snd.channels.toTypedArray(), outputs = snd.channels.size)
        else -> kr("Out", bus.c, snd, outputs = 1)
    }
}

object In {
    fun ar(bus: Int = 0, channels: Int) = ar("In", bus.c, outputs = channels)
    fun kr(bus: Int, channels: Int) = kr("In", bus.c, outputs = channels)
}

fun UGen.clip(min: UGen = 0f.c, max: UGen = 1f.c) =
    createRegularUgen("Clip", rate, arrayOf(this, min, max), outputs = 1)

fun UGen.linexp(sourceMin: UGen, sourceMax: UGen, targetMin: UGen, targetMax: UGen) =
    createRegularUgen(
        "LinExp",
        rate,
        arrayOf(this.clip(sourceMin, sourceMax), sourceMin, sourceMax, targetMin, targetMax),
        outputs = 1
    )

fun env(levels: List<UGen>, times: List<UGen>, curve: Curve, releaseNode: Int = -99, loopNode: Int = -99): Env =
    Env(levels, times, List(times.size) { curve }, releaseNode, loopNode)

fun env(levels: List<UGen>, times: List<UGen>, curve: String = "lin", releaseNode: Int = -99, loopNode: Int = -99) {
    env(levels, times, Curve.fromString(curve), releaseNode, loopNode)
}

fun env(levels: UGen, times: UGen, curve: Curve = LinearCurve, releaseNode: Int = -99, loopNode: Int = -99): Env {
    require(levels is MultiChannelUGen) { "Levels must be a MultiChannelUGen" }
    require(times is MultiChannelUGen) { "Times must be a MultiChannelUGen" }
    return env(levels.channels, times.channels, curve, releaseNode, loopNode)
}

fun env(levels: UGen, times: UGen, curve: String, releaseNode: Int = -99, loopNode: Int = -99): Env {
    return env(levels, times, Curve.fromString(curve), releaseNode, loopNode)
}

fun Env.kr(
    doneAction: Done = Done.NONE, gate: UGen = 1.c,
    timeScale: UGen = 1.c, levelScale: UGen = 1.c, levelBias: UGen = 0.c
) = EnvGen(Rate.Control, this, gate, levelScale, levelBias, timeScale, doneAction)

val Env.kr get() = kr(Done.NONE)

fun Env.ar(
    doneAction: Done = Done.NONE, gate: UGen = 1.c,
    timeScale: UGen = 1.c, levelScale: UGen = 1.c, levelBias: UGen = 0.c
) = EnvGen(Rate.Audio, this, gate, levelScale, levelBias, timeScale, doneAction)

val Env.ar get() = ar(Done.NONE)