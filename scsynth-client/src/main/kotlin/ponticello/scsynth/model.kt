package ponticello.scsynth

const val VERSION = 2
const val ID = "SCgf"

data class CompiledSynthDef(
    val name: String,
    val constants: List<Float>,
    val parameters: List<Parameter>,
    val ugens: List<UGenSpec>,
    val variants: List<Variant>
) {
    override fun toString(): String = "SynthDef '$name': \n" +
            "  constants: $constants\n" +
            "  parameters:\n    ${parameters.joinToString("\n    ")}\n" +
            "  ugens:\n    ${ugens.withIndex().joinToString("\n    ") { (idx, ugen) -> "v_$idx = $ugen" }}\n" +
            "  variants: $variants\n"
}

data class Parameter(val index: Int, val name: String) {
    lateinit var defaultValues: List<Float>

    override fun toString(): String {
        val indices = if (defaultValues.size > 1) "$index..${index + defaultValues.size - 1}" else "$index"
        return "$name [$indices] = ${defaultValues.singleOrNull() ?: defaultValues}"
    }
}

enum class Rate { Scalar, Control, Audio, }

sealed class InputSpec {
    companion object {
        fun fromIndices(index1: Int, index2: Int, constants: List<Float>) = when (index1) {
            -1 -> ConstantInputSpec(index2, constants[index2])
            else -> UGenInputSpec(index1, index2)
        }
    }
}

data class ConstantInputSpec(val index: Int, val value: Float) : InputSpec() {
    override fun toString(): String = "$value [$index]"
}

data class UGenInputSpec(val index: Int, val outputIndex: Int) : InputSpec() {
    override fun toString(): String = "v_$index[$outputIndex]"
}

data class UGenSpec(
    val className: String,
    val rate: Rate,
    val specialIndex: Int,
    val inputs: List<InputSpec>,
    val outputRates: List<Rate>
) {
    override fun toString(): String = when (className) {
        "Control" ->
            if (outputRates.size > 1) "$rate [$specialIndex..${specialIndex + outputRates.size - 1}]"
            else "$rate [$specialIndex]"

        else -> "$className[$rate${if (specialIndex != 0) ", $specialIndex" else ""}] ${inputs.joinToString(", ")} -> $outputRates"
    }
}

data class Variant(val name: String, val parameterValues: List<Float>)

/*
EnvGen format:
 EnvGen, rate, gate, levelScale, levelBias, timeScale, doneAction,
 initial_level, N, releaseNode, loopNode, [lvl, time, curve_type, curve] * N

Env shapes:

0: step
1: lin
2: exp
3: -
4: welch
5: x^a
6: squared
7: cubed
8: hold

* */