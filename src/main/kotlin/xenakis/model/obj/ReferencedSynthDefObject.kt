package xenakis.model.obj

import hextant.context.Context
import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.reactiveValue
import reaktive.value.reactiveVariable
import xenakis.impl.*
import xenakis.model.flow.FlowType
import xenakis.sc.BufferControlSpec
import xenakis.sc.BusControlSpec
import xenakis.sc.NumericalControlSpec
import xenakis.sc.Rate
import xenakis.sc.client.ScWriter
import xenakis.sc.client.SuperColliderClient
import xenakis.ui.registry.ParameterDefList

@Serializable
class ReferencedSynthDefObject(
    private val _name: String,
    override val color: ReactiveVariable<@Serializable(with = ColorSerializer::class) Color>,
) : SynthDefObject, AbstractNamedObject() {
    override lateinit var parameters: ParameterDefList
        private set

    override fun initialize(context: Context) {
        super.initialize(context)
        parameters = ParameterDefList()
        parameters.initialize(context)
        updateParameters()
    }

    override fun ScWriter.createObject() {}

    override fun ScWriter.freeObject() {}

    override val name: ReactiveValue<String>
        get() = reactiveValue(_name)

    override fun ScWriter.sync() {
        updateParameters()
    }

    override fun sync() {
        updateParameters()
    }

    private fun updateParameters() {
        if (!canSuperColliderTalkToMe) {
            Logger.error("SuperCollider cannot send data to me")
            return
        }
        val newParameters = getSynthDefParameters(_name)
        for (param in parameters.toList()) {
            parameters.remove(param)
        }
        for (param in newParameters) {
            param.initialize(context)
            parameters.add(param)
        } //TODO modify based on diff
    }

    private fun getSynthDefParameters(name: String): MutableList<ParameterDefObject> =
        with(context[SuperColliderClient]) {
            val params = mutableListOf<ParameterDefObject>()
            val nParams = try {
                send("controls", listOf(name)).get().toInt()
            } catch (ex: Exception) {
                Logger.error("Failed to get number of parameters for SynthDef #$name", ex)
                return@with mutableListOf()
            }
            for (i in 0 until nParams) {
                try {
                    val controlRef = listOf(name, i)
                    val paramName = send("controlName", controlRef).get()
                    if (paramName == "duration") continue
                    val default = send("controlDefault", controlRef).get()
                    val spec = when (paramName) {
                        "in" -> BusControlSpec(Rate.Audio, 2, FlowType.In)
                        "out" -> BusControlSpec(Rate.Audio, 2, FlowType.Out)
                        "bus" -> BusControlSpec(Rate.Audio, 2, FlowType.Out)
                        "buf" -> BufferControlSpec(2)
                        else -> {
                            val min = send("controlMinval", listOf(name, paramName)).get().toDouble()
                            val max = send("controlMaxval", listOf(name, paramName)).get().toDouble()
                            val warp = send("controlWarp", listOf(name, paramName)).get().toWarp()
                            val step = send("controlStep", listOf(name, paramName)).get().toDouble()
                            NumericalControlSpec(default.toDouble(), min, max, step.toDecimal(), warp, randomColor())
                        }
                    }
                    val param = ParameterDefObject(paramName, spec)
                    params.add(param)
                } catch (ex: Exception) {
                    Logger.error("Failed to get parameter for SynthDef #$name", ex)
                }
            }
            return params
        }

    override fun toString(): String = "ReferencedSynthDef #$name"

    companion object {
        private val instances = mutableMapOf<String, ReferencedSynthDefObject>()

        fun get(name: String): SynthDefObject = instances.getOrPut(name) {
            val associatedColor = reactiveVariable(randomColor())
            return ReferencedSynthDefObject(name, associatedColor)
        }
    }
}