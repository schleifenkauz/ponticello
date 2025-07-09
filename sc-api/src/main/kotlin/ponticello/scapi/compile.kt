package ponticello.scapi

import ponticello.scsynth.*
import java.io.DataOutputStream
import java.io.File
import java.util.*

class SynthDefCompiler {
    private val constantMap = mutableMapOf<Float, Int>()
    private val constants = mutableListOf<Float>()

    private var parameterValues = 0
    private val parameters = mutableListOf<Parameter>()

    private val ugens = mutableListOf<UGenSpec>()
    private val ugenMap = IdentityHashMap<UGen, Int>()

    private fun compile(ugen: UGen): List<InputSpec> {
        return when (ugen) {
            is ConstantUGen -> {
                val idx = constantMap.getOrPut(ugen.value) {
                    constants.add(ugen.value)
                    constants.indices.last
                }
                listOf(ConstantInputSpec(idx, ugen.value))
            }

            is ControlUGen -> {
                val parameterIndex = parameterValues
                val n = ugen.defaultValues.size
                val idx = cacheUGen(ugen) {
                    val param = Parameter(parameterIndex, ugen.parameterName)
                    param.defaultValues = ugen.defaultValues
                    parameters.add(param)
                    parameterValues += ugen.defaultValues.size
                    UGenSpec(
                        "Control",
                        ugen.rate,
                        parameterIndex,
                        inputs = emptyList(),
                        outputRates = List(n) { ugen.rate })
                }
                (0 until n).map { outputIndex -> UGenInputSpec(idx, outputIndex) }
            }

            is OutputProxy -> {
                val outputs = compile(ugen.source)
                listOf(outputs[ugen.index])
            }

            is BinaryOpUGen -> {
                val idx = cacheUGen(ugen) {
                    UGenSpec(
                        "BinaryOpUGen", ugen.rate, ugen.operator.ordinal,
                        inputs = compile(ugen.left) + compile(ugen.right), outputRates = listOf(ugen.rate)
                    )
                }
                listOf(UGenInputSpec(idx, outputIndex = 0))
            }

            is UnaryOpUgen -> {
                val idx = cacheUGen(ugen) {
                    UGenSpec(
                        "UnaryOpUGen", ugen.rate, ugen.operator.ordinal,
                        inputs = compile(ugen.input), outputRates = listOf(ugen.rate)
                    )
                }
                listOf(UGenInputSpec(idx, outputIndex = 0))
            }

            is RegularUGen -> {
                val idx = cacheUGen(ugen) {
                    UGenSpec(
                        ugen.className, ugen.rate, 0,
                        inputs = ugen.inputs.flatMap { compile(it) }, outputRates = ugen.outputRates
                    )
                }
                ugen.outputRates.indices.map { outputIndex -> UGenInputSpec(idx, outputIndex) }
            }

            is MultiChannelUGen -> {
                ugen.channels.map { compile(it) }.flatten() //TODO is it ok to flatten this?
            }
        }
    }

    private fun cacheUGen(ugen: UGen, createSpec: () -> UGenSpec): Int {
        val idx = ugenMap.getOrPut(ugen) {
            val spec = createSpec()
            ugens.add(spec)
            ugens.indices.last
        }
        return idx
    }

    fun compileSynthDef(def: SynthDef): CompiledSynthDef {
        compile(def.ugenGraph())
        return CompiledSynthDef(def.name, constants, parameters, ugens, variants = emptyList())
    }
}

fun main() {
    val compiler = SynthDefCompiler()
    val def = SynthDef("quinte2") {
        val amp = SinOsc.kr(0.5.c).linexp((-1).c, 1.c, 0.01.c, 0.3.c)
        var snd = (SinOsc.ar(c(440, 660))).sum
        snd = snd * amp
        val pan = SinOsc.kr("pan_rate".kr(0.1f))
        snd = Pan2.ar(snd, pan)
        Out.ar(0, snd)
    }
    println(def.ugenGraph())
    val compiled = compiler.compileSynthDef(def)
    println(compiled)

    val file = File("C:\\Users\\nikok\\AppData\\Local\\SuperCollider\\synthdefs\\quinte2.scsyndef")
    DataOutputStream(file.outputStream()).use { output ->
        val writer = SynthDefWriter(output)
        writer.write(listOf(compiled))
    }

    readSynthDefs(file.absolutePath).forEach { println(it) }
}

/*
    v_0 = SinOsc[Audio] 440.0 [0], 0.0 [1] -> [Audio]
    v_1 = SinOsc[Audio] 660.0 [2], 0.0 [1] -> [Audio]
    v_2 = BinaryOpUGen #0 [Audio]: v_0[0], v_1[0] -> [Audio]
    v_3 = SinOsc[Control] 1.0 [3], 0.0 [1] -> [Control]
    v_4 = Clip[Control] v_3[0], -1.0 [4], 1.0 [3] -> [Control]
    v_5 = LinExp[Control] v_4[0], -1.0 [4], 1.0 [3], 0.01 [5], 1.0 [3] -> [Control]
    v_6 = BinaryOpUGen #2 [Audio]: v_2[0], v_5[0] -> [Audio]
    v_7 = Control [0]
    v_8 = SinOsc[Control] v_7[0], 0.0 [1] -> [Control]
    v_9 = Pan2[Audio] v_6[0], v_8[0], 1.0 [3] -> [Audio, Audio]
    v_10 = Out[Audio] 0.0 [1], v_9[0], v_9[1] -> []
* */