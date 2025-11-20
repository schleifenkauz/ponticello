package ponticello.model

import bundles.set
import hextant.core.HextantCore
import io.mockk.mockk
import ponticello.impl.asTime
import ponticello.impl.toDecimal
import ponticello.model.instr.InstrumentReference
import ponticello.model.instr.InstrumentRegistry
import ponticello.model.registry.ObjectReference
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.score.ScoreObject
import ponticello.model.score.SoundProcess
import ponticello.model.server.BusRegistry
import ponticello.sc.client.SuperColliderClient

object Utils {
    fun createDummyObject(name: String): ScoreObject {
        val dummy1 = SoundProcess.create(
            name, InstrumentReference.UserDefined(ObjectReference("default"))
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
                mutableListOf(/*InstrumentRegistry.defaultInstrument()*/)
            ).also { it.initialize(this) }
        )
        set(BusRegistry, BusRegistry.createDefault().also { it.initialize(this) })
    }
}