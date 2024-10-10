package xenakis.model

import bundles.set
import hextant.context.ContextImpl
import hextant.undo.UndoManager
import reaktive.value.reactiveVariable
import xenakis.impl.asTime
import xenakis.impl.toDecimal

object Utils {
    val defaultGroup = GroupObject.DEFAULT.createReference()

    fun createDummyObject(name: String): ScoreObject {
        val dummy1 = SynthObject(
            mutableName = reactiveVariable(name),
            synthDefRef = reactiveVariable(ObjectReference("default")),
            controls = SynthControls(mutableMapOf("group" to GroupControl(reactiveVariable(defaultGroup))))
        )
        dummy1.setInitialSize(10.0.asTime, 100.0.toDecimal())
        return dummy1
    }

    fun createContext() = ContextImpl(null).apply {
        set(UndoManager, UndoManager.newInstance())
        set(ScoreObjectRegistry, ScoreObjectRegistry(mutableListOf()).also { it.initialize(this) })
        set(InstrumentRegistry, InstrumentRegistry.createDefault())
        set(GroupRegistry, GroupRegistry.createDefault())
    }
}