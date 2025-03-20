package xenakis.model.obj

import hextant.context.Context
import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.list.MutableReactiveList
import reaktive.list.ReactiveList
import reaktive.list.reactiveList
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

@Serializable
class ReferencedSynthDefObject(
    private val _name: String,
    override val color: ReactiveVariable<@Serializable(with = ColorSerializer::class) Color>
) : SynthDefObject, InstrumentObject, AbstractNamedObject() {
    @Transient
    private var _parameters: MutableReactiveList<ParameterDefObject> = reactiveList()

    override fun initialize(context: Context) {
        super.initialize(context)
        updateParameters()
    }

    override fun ScWriter.createObject() {}

    override fun ScWriter.freeObject() {}

    override val name: ReactiveValue<String>
        get() = reactiveValue(_name)

    override val parameters: ReactiveList<ParameterDefObject>
        get() = _parameters

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
        val parameters = getSynthDefParameters(_name)
        _parameters.now.clear()
        _parameters.now.addAll(parameters)
        for (param in parameters) {
            param.initialize(context)
        }
    }

    private fun getSynthDefParameters(name: String): MutableList<ParameterDefObject> =
        with(context[SuperColliderClient]) {
            val params = mutableListOf<ParameterDefObject>()
            val nParams = send("controls", listOf(name)).get().toInt()
            for (i in 0 until nParams) {
                val controlRef = listOf(name, i)
                val paramName = send("controlName", controlRef).get()
                if (paramName == "duration") continue
                val default = send("controlDefault", controlRef).get()
                val spec = when (paramName) {
                    "in" -> BusControlSpec(Rate.Audio, 2, FlowType.In)
                    "out" -> BusControlSpec(Rate.Audio, 2, FlowType.Out)
                    "bus" -> BusControlSpec(Rate.Audio, 2, FlowType.Out)
                    "buf" -> BufferControlSpec()
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