package xenakis.model.score

import hextant.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.Decimal
import xenakis.impl.copy
import xenakis.impl.writeCode
import xenakis.model.Settings
import xenakis.model.flow.NodePlacement
import xenakis.model.obj.ParameterizedObject
import xenakis.model.obj.ParameterizedObjectDef
import xenakis.model.obj.ProcessDefObject
import xenakis.model.obj.ProcessDefReference
import xenakis.model.player.LiveProcessUpdater
import xenakis.model.registry.ProcessDefRegistry
import xenakis.model.score.controls.ParameterControl
import xenakis.model.score.controls.ParameterControl.CodegenContext
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
        get() = "~process_"

    val processDef get() = processDefRef.now.get() ?: ProcessDefObject.unresolved()

    override val def: ParameterizedObjectDef
        get() = processDef

    override val associatedControls: Map<String, ParameterControl>
        get() = controls.controlMap

    @Transient
    private lateinit var listener: LiveProcessUpdater

    override fun getSpec(parameter: String): ControlSpec? = controls.getOrNull(parameter)?.spec?.now

    override fun initialize(context: Context) {
        super.initialize(context)
        processDefRef.now.resolve(context[ProcessDefRegistry])
        controls.initialize(context, this)
        listener = LiveProcessUpdater(this)
    }

    override fun onLoadedIntoRegistry() {
        super<ScoreObject>.onLoadedIntoRegistry()
        listener.startListening()
    }

    override fun onRemoved() {
        super<ScoreObject>.onRemoved()
        listener.stopListening()
    }

    override fun validate(): Boolean = controls.validate()

    override fun writeCode(uniqueName: String, placement: NodePlacement?, cutoff: Decimal): String {
        val superColliderName = "~process_$uniqueName"
        val associatedServerObjects = mutableListOf<String>()
        return writeCode {
            appendBlock("$superColliderName = Task", endLine = false) {
                val latency = context[Settings].serverLatency.get()
                for (control in controls) {
                    val spec = control.spec.now!!
                    val name = control.name.now
                    with(control.now) {
                        generatePreparationCode(
                            this@ProcessObject, uniqueName,
                            name, spec, associatedServerObjects,
                            context = CodegenContext.Process
                        )
                    }
                }
                +"$latency.wait"
                append("${processDefRef.now.superColliderName}.value(t: $cutoff, duration: $duration")
                for (control in controls) {
                    if (!def.hasParameter(control.name.now)) continue
                    val name = control.name.now
                    val spec = control.spec.now!!
                    val arg = control.now.generateArgumentExpr(
                        this@ProcessObject, uniqueName, name, spec,
                        context = CodegenContext.Process
                    )
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