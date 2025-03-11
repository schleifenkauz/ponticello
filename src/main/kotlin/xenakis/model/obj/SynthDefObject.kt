package xenakis.model.obj

import hextant.context.Context
import javafx.scene.input.DataFormat
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.model.registry.GroupRegistry
import xenakis.model.registry.ObjectReference
import xenakis.model.score.GroupControl
import xenakis.model.score.ParameterControls
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
        context: Context, defaultGroup: ObjectReference?, defaultBus: ObjectReference?
    ): ParameterControls {
        val controls = super.defaultControls(context, defaultGroup, defaultBus)
        val group = defaultGroup ?: context[GroupRegistry].getDefault().reference()
        controls.controlMap["group"] = GroupControl(reactiveVariable(group))
        return controls
    }

    override fun copy(name: String): SynthDefObject

    companion object {
        val DATA_FORMAT = DataFormat("synth-def")
    }
}