package ponticello.scapi

import ponticello.scsynth.*
import java.util.*

class SynthDefCompiler {
    private val constantMap = mutableMapOf<Float, Int>()
    private val constants = mutableListOf<Float>()

    private var parameterValues = 0
    private val parameters = mutableListOf<Parameter>()

    private val ugens = mutableListOf<UGenSpec>()
    private val ugenMap = IdentityHashMap<UGen, Int>()

    private fun compile(ugen: UGen, outputIndex: Int = -1): InputSpec {
        val outputIndex = when (ugen.numChannels) {
            0 -> throw AssertionError("Error compiling $ugen: numChannels = 0")
            1 -> {
                require(outputIndex == -1) { "Error compiling $ugen: outputIndex = $outputIndex but not a multi channel UGen" }
                0
            }

            else -> {
                require(outputIndex >= 0) { "Error compiling $ugen: $outputIndex is negative but numChannels = ${ugen.numChannels}" }
                outputIndex
            }

        }
        return when (ugen) {
            is ConstantUGen -> {
                val idx = constantMap.getOrPut(ugen.value) {
                    constants.add(ugen.value)
                    constants.indices.last
                }
                ConstantInputSpec(idx, ugen.value)
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
                UGenInputSpec(idx, outputIndex)
            }

            is OutputProxy -> {
                compile(ugen.source, ugen.index)
            }

            is EnvGen -> {
                val idx = cacheUGen(ugen) {
                    val inputs = buildList {
                        add(compile(ugen.gate))
                        add(compile(ugen.levelScale))
                        add(compile(ugen.levelBias))
                        add(compile(ugen.timeScale))
                        add(compile(ugen.doneAction.ordinal.c))

                        val levels = ugen.envelope.levels
                        val times = ugen.envelope.times
                        val curve = ugen.envelope.curve

                        require(levels.size == times.size + 1)
                        require(curve.size == times.size)

                        add(compile(levels[0]))
                        add(compile(times.size.c))
                        add(compile(ugen.envelope.releaseNode.c))
                        add(compile(ugen.envelope.loopNode.c))

                        for (i in times.indices) {
                            add(compile(levels[i + 1]))
                            add(compile(times[i]))
                            add(compile(curve[i].type.c))
                            add(compile(curve[i].value.c))
                        }
                    }
                    UGenSpec(
                        "EnvGen", ugen.rate, 0, inputs,
                        outputRates = listOf(ugen.rate)
                    )
                }
                UGenInputSpec(idx)
            }

            is BinaryOpUGen -> {
                val idx = cacheUGen(ugen) {
                    val inputs = listOf(compile(ugen.left), compile(ugen.right))
                    UGenSpec(
                        "BinaryOpUGen", ugen.rate, ugen.operator.ordinal, inputs,
                        outputRates = listOf(ugen.rate)
                    )
                }
                UGenInputSpec(idx)
            }

            is UnaryOpUgen -> {
                val idx = cacheUGen(ugen) {
                    UGenSpec(
                        "UnaryOpUGen", ugen.rate, ugen.operator.ordinal,
                        inputs = listOf(compile(ugen.input)), outputRates = listOf(ugen.rate)
                    )
                }
                UGenInputSpec(idx)
            }

            is RegularUGen -> {
                val idx = cacheUGen(ugen) {
                    UGenSpec(
                        ugen.className, ugen.rate, 0,
                        inputs = ugen.inputs.map { compile(it) }, outputRates = ugen.outputRates
                    )
                }
                UGenInputSpec(idx, outputIndex)
            }

            is MultiChannelUGen -> {
                compile(ugen.channels[outputIndex])
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
        val ugen = def.ugenGraph()
        if (ugen.numChannels != 1) compile(ugen, outputIndex = 0)
        else compile(ugen, outputIndex = -1)
        return CompiledSynthDef(def.name, constants, parameters, ugens, variants = emptyList())
    }
}

