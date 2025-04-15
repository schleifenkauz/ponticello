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
import xenakis.model.registry.ProcessDefRegistry
import xenakis.model.score.controls.ParameterControl
import xenakis.sc.ControlSpec

@Serializable
class ProcessObject(
    @SerialName("name") override val mutableName: ReactiveVariable<String>,
    @SerialName("processDef") val processDefRef: ReactiveVariable<ProcessDefReference>,
    override val controls: ParameterControlList,
) : ScoreObject(), ParameterizedObject {
    override val type: String
        get() = "process"

    override val superColliderPrefix: String
        get() = "~process"

    val processDef get() = processDefRef.now.get() ?: ProcessDefObject.unresolved(context)

    override val def: ParameterizedObjectDef
        get() = processDef

    override val associatedControls: Map<String, ParameterControl>
        get() = controls.controlMap

    override fun getSpec(parameter: String): ControlSpec? = controls.getOrNull(parameter)?.spec?.now

    override fun initialize(context: Context) {
        super.initialize(context)
        processDefRef.now.resolve(context[ProcessDefRegistry])
        controls.initialize(context, this)
    }

    override fun validate(): Boolean = controls.validate()

    override fun writeCode(info: ScoreObjectInfo): String {
        val uniqueName = info.uniqueName(this)
        val superColliderName = superColliderName(info.suffix)
        val associatedServerObjects = mutableListOf<String>()
        return code {
            appendBlock("$superColliderName = Task", endLine = false) {
                val latency = context[Settings].serverLatency.get()
                for (control in controls) {
                    val spec = control.spec.now!!
                    val name = control.name.now
                    with(control.now) {
                        generatePreparationCode(
                            this@ProcessObject, uniqueName,
                            name, spec, associatedServerObjects
                        )
                    }
                }
                +"$latency.wait"
                val t0 = info.cutoff
                append("${processDefRef.now.superColliderName}.value(t: $t0, duration: $duration")
                for (control in controls) {
                    if (!def.hasParameter(control.name.now)) continue
                    val name = control.name.now
                    val spec = control.spec.now!!
                    val arg = control.now.generateArgumentExpr(this@ProcessObject, uniqueName, name, spec)
                    append(", $name: ")
                    arg.code(writer, context)
                }
                appendLine(");")
            }
            +".play"
        }
    }

    override fun doClone(newName: String): ScoreObject =
        ProcessObject(reactiveVariable(newName), processDefRef.copy(), controls.copy())
}