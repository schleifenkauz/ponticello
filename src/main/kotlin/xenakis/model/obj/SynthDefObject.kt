package xenakis.model.obj

import hextant.context.Context
import javafx.scene.input.DataFormat
import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.model.registry.GroupRegistry
import xenakis.model.registry.ObjectRegistry
import xenakis.model.registry.SynthDefRegistry
import xenakis.model.registry.reference
import xenakis.model.score.controls.GroupControl
import xenakis.model.score.controls.ParameterControl
import xenakis.sc.GroupControlSpec

@Serializable
sealed interface SynthDefObject : ParameterizedObjectDef, SuperColliderObject {
    val color: ReactiveVariable<Color>

    override val registry: ObjectRegistry<*>?
        get() = context[SynthDefRegistry]

    override val superColliderName: String
        get() = "\\${name.now}"

    override fun getParameter(name: String): ParameterDefObject? = when (name) {
        "group" -> ParameterDefObject("group", GroupControlSpec)
        else -> super.getParameter(name)
    }

    override fun hasParameter(name: String): Boolean = name == "group" || super.hasParameter(name)

    override fun defaultControls(
        context: Context, defaultGroup: GroupReference?, defaultBus: BusReference?
    ): MutableList<Pair<String, ParameterControl>> {
        val controls = super.defaultControls(context, defaultGroup, defaultBus)
        val group = defaultGroup ?: context[GroupRegistry].getDefault().reference()
        controls.add("group" to GroupControl(reactiveVariable(group)))
        return controls
    }

    companion object {
        val DATA_FORMAT = DataFormat("synth-def")
    }
}