package ponticello.scapi

import ponticello.scsynth.Rate

sealed class UGen {
    abstract val rate: Rate

    open val numChannels: Int get() = 1
}

data class ConstantUGen(val value: Float) : UGen() {
    override val rate: Rate
        get() = Rate.Scalar

    override fun toString(): String = value.toString()
}

data class RegularUGen(
    val className: String, override val rate: Rate,
    val inputs: List<UGen>, val outputRates: List<Rate>
) : UGen() {
    override val numChannels: Int
        get() = outputRates.size

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
    override val numChannels: Int
        get() = defaultValues.size

    override fun toString(): String = "$parameterName.${rate.code}($defaultValues)"
}

sealed class Curve(val type: Int) {
    open val value: Float get() = 0f

    companion object {
        fun fromString(str: String) = when (str) {
            "step" -> StepCurve
            "linear", "lin" -> LinearCurve
            "exp", "exponential" -> ExpCurve
            "sine", "sin" -> SineCurve
            "welch", "wel" -> WelchCurve
            else -> throw AssertionError("Unknown curve type: $str")
        }
    }
}

object StepCurve : Curve(type = 0) {
    override fun toString(): String = "step"
}

object LinearCurve : Curve(type = 1) {
    override fun toString(): String = "lin"
}

object ExpCurve : Curve(type = 2) {
    override fun toString(): String = "exp"
}

object SineCurve : Curve(type = 3) {
    override fun toString(): String = "sin"
}

object WelchCurve : Curve(type = 4) {
    override fun toString(): String = "wel"
}

data class CustomCurve(val exponent: Float) : Curve(5) {
    override val value: Float
        get() = exponent

    override fun toString(): String = "x^$exponent"
}

data class Env(
    val levels: List<UGen>, val times: List<UGen>, val curve: List<Curve>,
    val releaseNode: Int, val loopNode: Int
) {
    companion object {
        fun perc(attack: UGen, release: UGen, curve: Curve, level: UGen) =
            env(listOf(0.c, level, 0.c), listOf(attack, release), curve)
    }
}

enum class Done {
    NONE, PAUSE_SELF, FREE_SELF;
}

data class EnvGen(
    override val rate: Rate, val envelope: Env, val gate: UGen,
    val levelScale: UGen, val levelBias: UGen, val timeScale: UGen,
    val doneAction: Done
) : UGen()

data class MultiChannelUGen(val channels: List<UGen>) : UGen() {
    override val rate: Rate
        get() = channels.maxOf { ch -> ch.rate }

    override val numChannels: Int
        get() = channels.size

    init {
        require(channels.size > 1) { "MultiChannelUGen must have at least 2 channels but has \n $channels" }
    }

    override fun toString(): String = channels.toString()
}

data class OutputProxy(val source: UGen, val index: Int) : UGen() {
    override val rate: Rate
        get() = source.rate

    override fun toString(): String = "$source[$index]"
}

data class SynthDef(val name: String, val ugenGraph: () -> UGen)