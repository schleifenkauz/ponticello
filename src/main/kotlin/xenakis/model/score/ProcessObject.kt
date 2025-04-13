package xenakis.model.score

import hextant.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.code
import xenakis.impl.copy
import xenakis.model.Settings
import xenakis.model.flow.ScoreObjectInfo
import xenakis.model.obj.ParameterizedObject
import xenakis.model.obj.ParameterizedObjectDef
import xenakis.model.obj.ProcessDefObject
import xenakis.model.obj.ProcessDefReference
import xenakis.model.registry.GroupRegistry
import xenakis.model.registry.ProcessDefRegistry
import xenakis.sc.*

@Serializable
class ProcessObject(
    @SerialName("name") override val mutableName: ReactiveVariable<String>,
    @SerialName("processDef") val processDefRef: ReactiveVariable<ProcessDefReference>,
    override val controls: ParameterControlList,
) : ScoreObject(), ParameterizedObject {
    override val type: String
        get() = "process"

    val processDef get() = processDefRef.now.get() ?: ProcessDefObject.unresolved(context)

    override val def: ParameterizedObjectDef
        get() = processDef

    override val associatedControls: Map<String, ParameterControl>
        get() = controls.controlMap

    override fun getSpec(parameter: String): ControlSpec? = super<ParameterizedObject>.getSpec(parameter)

    override fun initialize(context: Context) {
        super.initialize(context)
        processDefRef.now.resolve(context[ProcessDefRegistry])
        controls.initialize(context, this)
    }

    override fun writeCode(info: ScoreObjectInfo): String = code {
        //TODO validated before generating code
        val arguments = controls.joinToString(", ") { control ->
            val name = control.name.now
            val ctrl = control.now
            val spec = control.spec.now
            val timeParameter = listOf(Identifier("t"))
            val expr = when (ctrl) {
                is CustomControl -> {
                    val expr = ctrl.expr.editor.result.now
                    val body = expr as? CodeBlock ?: CodeBlock(emptyList(), listOf(expr))
                    ScFunction(timeParameter, body)
                }

                is EnvelopeControl -> {
                    spec as? NumericalControlSpec ?: error("No numerical control spec for argument $name")
                    val varName = "env_$name"
                    +"var $varName = ${ctrl.points.code(warp = spec.warp)}"
                    lambda("t") { Identifier(varName).send("at", Identifier("t")) }
                }

                is GroupControl -> {
                    val group = ctrl.group.now.get() ?: context[GroupRegistry].getDefault()
                    Identifier(group.superColliderName)
                }

                is SingleBusValueControl -> {
                    val bus = ctrl.bus.now.get() ?: error("Unresolved bus ${ctrl.bus.now}")
                    lambda("t") { Identifier(bus.superColliderName).send("getSynchronous") }
                }

                is BusValueControl ->  {
                    error("BusValueControl is not allowed on process objects")
                }

                is BufferControl -> {
                    val buffer = ctrl.sample.now.get() ?: error("Unresolved sample ${ctrl.sample.now}")
                    Identifier(buffer.superColliderName)
                }

                is BusControl -> {
                    val bus = ctrl.bus.now.get() ?: error("Unresolved bus ${ctrl.bus.now}")
                    Identifier(bus.superColliderName)
                }

                is ValueControl -> DecimalLiteral(ctrl.value.now)
            }
            "$name: ${expr.code(context)}"
        }
        appendBlock("Task", endLine = false) {
            val latency = context[Settings].serverLatency.get()
            +"$latency.wait"
            +"${processDefRef.now.superColliderName}.value(t: 0, duration: $duration, $arguments)"
        }
        +".play"
    }

    override fun doClone(newName: String): ScoreObject =
        ProcessObject(reactiveVariable(newName), processDefRef.copy(), controls.copy())
}