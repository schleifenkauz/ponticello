package xenakis.model.obj

import hextant.context.Context
import javafx.scene.input.DataFormat
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.model.registry.ObjectReference
import xenakis.model.score.GroupControl
import xenakis.model.score.ParameterControl
import xenakis.sc.GroupControlSpec

interface SynthDefObject : ParameterizedObjectDef, InstrumentObject {
    override val superColliderName: String
        get() = "\\${name.now}"

    override fun getParameter(name: String): ParameterDefObject? = when (name) {
        "group" -> ParameterDefObject("group", GroupControlSpec())
        else -> super.getParameter(name)
    }

    override fun hasParameter(name: String): Boolean = name == "group" || super.hasParameter(name)

    override fun defaultControls(
        context: Context, defaultGroup: GroupReference?, defaultBus: BusReference?
    ): MutableList<Pair<String, ParameterControl>> {
        val controls = super.defaultControls(context, defaultGroup, defaultBus)
        val group = defaultGroup ?: ObjectReference.none()
        controls.add("group" to GroupControl(reactiveVariable(group)))
        return controls
    }

    companion object {
        val DATA_FORMAT = DataFormat("synth-def")
    }
}