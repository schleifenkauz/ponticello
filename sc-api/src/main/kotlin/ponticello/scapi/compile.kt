package ponticello.scapi

import ponticello.scsynth.*
import java.util.*

class SynthDefCompiler {
    private val constantMap = mutableMapOf<Float, Int>()
    private val constants = mutableListOf<Float>()

    private val parameters = mutableListOf<Parameter>()

    private val ugens = mutableListOf<UGenSpec>()
    private val ugenMap = IdentityHashMap<UGen, Int>()

    private fun compile(ugen: UGen): InputSpec {
        return when (ugen) {
            is BinaryOpUGen -> TODO()
            is ConstantUGen -> {
                val idx = constantMap.getOrPut(ugen.value) {
                    constants.add(ugen.value)
                    constants.indices.last
                }
                ConstantInputSpec(idx, ugen.value)
            }

            is ControlUGen -> {
                val index = parameters.indexOfFirst { p -> p.name == ugen.parameterName }
                return UGenInputSpec(index, 0) //TODO
            }

            is UnaryOpUgen -> TODO()
            is RegularUGen -> {
                val idx = ugenMap.getOrPut(ugen) {
                    val spec = UGenSpec(
                        ugen.className, ugen.rate, 0,
                        inputs = ugen.inputs.map { compile(it) },
                        outputRates = emptyList() //TODO
                    )
                    ugens.add(spec)
                    ugens.indices.last
                }
                return UGenInputSpec(idx, 0)
            }
        }
    }

    fun compileSynthDef(name: String, ugen: UGen): CompiledSynthDef {
        compile(ugen)
        return CompiledSynthDef(name, constants, parameters, ugens, variants = emptyList())
    }
}