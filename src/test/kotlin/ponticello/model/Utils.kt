package ponticello.model

import bundles.set
import hextant.core.HextantCore
import io.mockk.mockk
import reaktive.value.reactiveVariable
import ponticello.impl.asTime
import ponticello.impl.toDecimal
import ponticello.model.registry.BusRegistry
import ponticello.model.registry.ObjectReference
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.registry.SynthDefRegistry
import ponticello.model.score.ParameterControlList
import ponticello.model.score.ScoreObject
import ponticello.model.score.SynthObject
import ponticello.sc.client.SuperColliderClient

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