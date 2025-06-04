package ponticello.model.score

import hextant.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ponticello.impl.Decimal
import ponticello.impl.copy
import ponticello.impl.writeCode
import ponticello.model.Settings
import ponticello.model.flow.NodePlacement
import ponticello.model.obj.ParameterDefObject
import ponticello.model.obj.ParameterizedObjectDef
import ponticello.model.obj.ProcessDefObject
import ponticello.model.obj.ProcessDefReference
import ponticello.model.registry.ProcessDefRegistry
import ponticello.model.score.controls.ParameterControl
import ponticello.model.score.controls.writeProcessCode
import ponticello.sc.ControlSpec
import reaktive.value.ReactiveVariable
import reaktive.value.now

@Serializable
class ProcessObject(
    @SerialName("processDef") val processDefRef: ReactiveVariable<ProcessDefReference>,
    override val controls: ParameterControlList,
) : ParameterizedScoreObject() {
    override val type: String
        get() = "process"

    override val superColliderPrefix: String
        get() = "~process_"

    val processDef get() = processDefRef.now.get() ?: ProcessDefObject.unresolved()

    override val def: ParameterizedObjectDef
        get() = processDef

    override fun getSpec(parameter: String): ControlSpec? = controls.getOrNull(parameter)?.spec?.now

    override fun initialize(context: Context) {
        super.initialize(context)
        processDefRef.now.resolve(context[ProcessDefRegistry])
        initializeControls()
    }

    override fun validate(): Boolean = controls.validate()

    override fun writeCode(
        uniqueName: String,
        placement: NodePlacement?,
        cutoff: Decimal,
        latency: Decimal,
        extraArguments: Map<ParameterDefObject, ParameterControl>
    ): String = writeCode {
        writeProcessCode(
            this@ProcessObject, uniqueName,
            cutoff, context[Settings].serverLatency.get(),
            extraArguments
        )
    }

    override fun doClone(): ScoreObject =
        ProcessObject(processDefRef.copy(), controls.copy())
}