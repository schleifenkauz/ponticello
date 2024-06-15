package xenakis.model

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
import xenakis.sc.BufferControlSpec
import xenakis.sc.BusControlSpec
import xenakis.sc.NumericalControlSpec

@Serializable
class ReferencedSynthDefObject(
    private val _name: String,
    override val color: ReactiveVariable<@Serializable(with = ColorSerializer::class) Color>
) : SynthDefObject {
    @Transient
    private var _parameters: MutableReactiveList<ParameterDefObject> = reactiveList()

    private lateinit var context: Context

    override fun onAdded(context: Context) {}

    override fun initialize(context: Context) {
        this.context = context
        updateParameters()
    }

    override fun onRemoved() {}

    override fun ScWriter.allocateServerObject() {}

    override fun ScWriter.freeServerObject() {}

    override val name: ReactiveValue<String>
        get() = reactiveValue(_name)

    override val parameters: ReactiveList<ParameterDefObject>
        get() = _parameters

    override fun sync(writer: ScWriter) {
        updateParameters()
    }

    override fun sync() {
        updateParameters()
    }

    private fun updateParameters() {
        async {
            val parameters = getSynthDefParameters(_name)
            _parameters.now.clear()
            _parameters.now.addAll(parameters)
            for (param in parameters) {
                param.initialize(context)
            }
        }
    }

    private fun getSynthDefParameters(name: String): MutableList<ParameterDefObject> =
        with(context[SuperColliderClient]) {
            val params = mutableListOf<ParameterDefObject>()
            val nParams = send("controls", listOf(name)).join().integer
            for (i in 0 until nParams) {
                val controlRef = listOf(name, i)
                val paramName = send("controlName", controlRef).join().string
                val type = send("controlType", controlRef).join().string
                val default = send("controlDefault", controlRef).join()
                val spec = when (type) {
                    "bus" -> BusControlSpec()
                    "buf" -> BufferControlSpec()
                    "num" -> {
                        val min = send("controlMinval", listOf(name, paramName)).join().double
                        val max = send("controlMaxval", listOf(name, paramName)).join().double
                        val warp = send("controlWarp", listOf(name, paramName)).join().warp
                        val step = send("controlStep", listOf(name, paramName)).join().double
                        NumericalControlSpec(default.double, min, max, warp, step, randomColor())
                    }

                    else -> error("unknown control type: $type")
                }
                val param = ParameterDefObject(paramName, spec)
                params.add(param)
            }
            return params
        }

    companion object {
        //SynthDef(\x, { \amp.kr(0.1, 0, false, [0.0, 1.0, 'lin', 0.1]) }).add;
        fun loadFromSynthDescLib(name: String): SynthDefObject {
            if (name in StandardSynthDefObject.all)
                return StandardSynthDefObject.all.getValue(name)
            else {
                val associatedColor = reactiveVariable(randomColor())
                return ReferencedSynthDefObject(name, associatedColor)
            }
        }

    }
}