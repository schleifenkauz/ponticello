package xenakis.model

import bundles.set
import hextant.context.Context
import hextant.undo.UndoManager
import reaktive.value.reactiveVariable
import xenakis.impl.asTime
import xenakis.impl.toDecimal
import xenakis.model.obj.GroupObject
import xenakis.model.registry.GroupRegistry
import xenakis.model.registry.InstrumentRegistry
import xenakis.model.registry.ObjectReference
import xenakis.model.registry.ScoreObjectRegistry
import xenakis.model.score.GroupControl
import xenakis.model.score.ParameterControls
import xenakis.model.score.ScoreObject
import xenakis.model.score.SynthObject

object Utils {
    val defaultGroup = GroupObject.DEFAULT.reference()

    fun createDummyObject(name: String): ScoreObject {
        val dummy1 = SynthObject(
            mutableName = reactiveVariable(name),
            synthDefRef = reactiveVariable(ObjectReference("default")),
            controls = ParameterControls(mutableMapOf("group" to GroupControl(reactiveVariable(defaultGroup))))
        )
        dummy1.setInitialSize(10.0.asTime, 100.0.toDecimal())
        return dummy1
    }

    fun createContext() = Context.create {
        set(UndoManager, UndoManager.newInstance())
        set(ScoreObjectRegistry, ScoreObjectRegistry(mutableListOf()).also { it.initialize(this) })
        set(
            InstrumentRegistry,
            InstrumentRegistry(mutableListOf(InstrumentRegistry.defaultInstrument()), reactiveVariable(null))
        )
        set(GroupRegistry, GroupRegistry.createDefault())
    }
}