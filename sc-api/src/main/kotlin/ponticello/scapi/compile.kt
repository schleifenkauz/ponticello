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
                val param = Parameter(parameterValues, ugen.parameterName)
                param.defaultValues = ugen.defaultValues
                parameters.add(param)
                parameterValues += ugen.defaultValues.size
                listOf(UGenInputSpec(0, 0)) //TODO
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
    val def = SynthDef("quinte") {
        val snd = (SinOsc.ar(c(440, 660))).sum
        Out.ar(0, snd)
    }
    println(def.ugenGraph())
    val compiled = compiler.compileSynthDef(def)
    println(compiled)

    val file = File("C:\\Users\\nikok\\AppData\\Local\\SuperCollider\\synthdefs\\quinte.scsyndef")
    DataOutputStream(file.outputStream()).use { output ->
        val writer = SynthDefWriter(output)
        writer.write(listOf(compiled))
    }
}