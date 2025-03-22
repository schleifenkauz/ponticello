package xenakis.model

import bundles.set
import hextant.core.HextantCore
import io.mockk.mockk
import reaktive.value.reactiveVariable
import xenakis.impl.asTime
import xenakis.impl.toDecimal
import xenakis.model.obj.GroupObject
import xenakis.model.registry.*
import xenakis.model.score.GroupControl
import xenakis.model.score.ParameterControls
import xenakis.model.score.ScoreObject
import xenakis.model.score.SynthObject
import xenakis.sc.client.SuperColliderClient

object Utils {
    val defaultGroup = GroupObject.DEFAULT.reference()

    fun createDummyObject(name: String): ScoreObject {
        val dummy1 = SynthObject(
            mutableName = reactiveVariable(name),
            synthDefRef = reactiveVariable(ObjectReference("default")),
            controls = ParameterControls.create("group" to GroupControl(reactiveVariable(defaultGroup)))
        )
        dummy1.setInitialSize(10.0.asTime, 100.0.toDecimal())
        return dummy1
    }

    fun createContext() = HextantCore.defaultContext().apply {
        set(SuperColliderClient, mockk<SuperColliderClient>(relaxed = true))
        set(
            ScoreObjectRegistry,
            ScoreObjectRegistry(mutableListOf()).also { it.initialize(this) })
        set(
            InstrumentRegistry,
            InstrumentRegistry(
                reactiveVariable(ObjectReference.none()),
                mutableListOf(/*InstrumentRegistry.defaultInstrument()*/)
            ).also { it.initialize(this) }
        )
        set(GroupRegistry, GroupRegistry.createDefault().also { it.initialize(this) })
        set(BusRegistry, BusRegistry.createDefault().also { it.initialize(this) })
    }
}