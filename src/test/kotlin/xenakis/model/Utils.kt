package xenakis.model

import bundles.set
import hextant.core.HextantCore
import io.mockk.mockk
import reaktive.value.reactiveVariable
import xenakis.impl.asTime
import xenakis.impl.toDecimal
import xenakis.model.registry.BusRegistry
import xenakis.model.registry.ObjectReference
import xenakis.model.registry.ScoreObjectRegistry
import xenakis.model.registry.SynthDefRegistry
import xenakis.model.score.ParameterControlList
import xenakis.model.score.ScoreObject
import xenakis.model.score.SynthObject
import xenakis.sc.client.SuperColliderClient

object Utils {
    fun createDummyObject(name: String): ScoreObject {
        val dummy1 = SynthObject(
            mutableName = reactiveVariable(name),
            synthDefRef = reactiveVariable(ObjectReference("default")),
            controls = ParameterControlList.create()
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
            SynthDefRegistry,
            SynthDefRegistry(
                mutableListOf(/*InstrumentRegistry.defaultInstrument()*/)
            ).also { it.initialize(this) }
        )
        set(BusRegistry, BusRegistry.createDefault().also { it.initialize(this) })
    }
}