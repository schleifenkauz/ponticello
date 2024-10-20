package xenakis.model.obj

import hextant.context.Context
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.model.obj.SuperColliderObject.LiveCycleType
import xenakis.model.registry.GroupRegistry
import xenakis.model.registry.ObjectReference
import xenakis.model.score.GroupControl
import xenakis.model.score.ParameterControls
import xenakis.sc.GroupControlSpec
import xenakis.sc.client.ScWriter

interface SynthDefObject : ParameterizedObjectDef, InstrumentObject {
    override val superColliderName: String
        get() = "\\${name.now}"
    override val liveCycleType: LiveCycleType
        get() = LiveCycleType.InterpreterBoot

    override fun getParameter(name: String): ParameterDefObject? = when (name) {
        "group" -> ParameterDefObject("group", GroupControlSpec())
        else -> super.getParameter(name)
    }

    override fun hasParameter(name: String): Boolean = name == "group" || super.hasParameter(name)

    override fun defaultControls(
        context: Context, defaultGroup: ObjectReference?, defaultBus: ObjectReference?
    ): ParameterControls {
        val controls = super.defaultControls(context, defaultGroup, defaultBus)
        val group = defaultGroup ?: context[GroupRegistry].getDefault().createReference()
        controls.controlMap["group"] = GroupControl(reactiveVariable(group))
        return controls
    }

    override fun ScWriter.addToServer() {
        allocateServerObject()
    }

    override fun copy(name: String): SynthDefObject
}