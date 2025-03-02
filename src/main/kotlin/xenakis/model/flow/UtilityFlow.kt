package xenakis.model.flow

import hextant.context.Context
import reaktive.value.ReactiveString
import reaktive.value.ReactiveVariable
import reaktive.value.binding.map
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.Decimal
import xenakis.impl.copy
import xenakis.impl.zero
import xenakis.model.obj.BusObject
import xenakis.model.obj.ReferencedSynthDefObject
import xenakis.model.registry.BusRegistry
import xenakis.model.registry.ObjectReference
import xenakis.model.score.BusControl
import xenakis.model.score.ConstantControl
import xenakis.model.score.ParameterControls
import xenakis.sc.client.ScWriter
import xenakis.sc.writeSynthCode

class UtilityFlow(
    private val busRef: ObjectReference,
    private val volumeDb: ReactiveVariable<Decimal>,
    private val muted: ReactiveVariable<Boolean>,
    val solo: ReactiveVariable<Boolean>,
) : AudioFlow() {
    override lateinit var associatedBus: BusObject
        private set

    override fun copyFor(associatedBus: BusObject): AudioFlow =
        UtilityFlow(busRef, volumeDb.copy(), muted.copy(), solo.copy())

    override lateinit var name: ReactiveString
        private set

    override fun initialize(context: Context) {
        super.initialize(context)
        busRef.resolve(context[BusRegistry])
        associatedBus = busRef.get()
        name = associatedBus.name.map { name -> "utility_$name" }
    }

    override fun ScWriter.writeCode(synthName: String, order: SynthOrder) {
        //TODO we need a way to mute other buses when something is soloed
        val volume = when {
            muted.now -> Decimal.NINF
            else -> volumeDb.now
        }
        val controls = ParameterControls(
            mutableMapOf(
                "bus" to BusControl(reactiveVariable(associatedBus.reference())),
                "volume" to ConstantControl(reactiveVariable(volume)),
            ),
        )
        writeSynthCode(
            synthName, ReferencedSynthDefObject.get("utility"), controls,
            context, order, duration = null
        )
    }

    override fun getConnectedBusses(vararg flowType: FlowType): Set<BusObject> =
        if (FlowType.InOut in flowType) setOf(associatedBus) else emptySet()

    companion object {
        fun createFor(bus: BusObject) = UtilityFlow(
            bus.reference(),
            volumeDb = reactiveVariable(zero(precision = 1)),
            muted = reactiveVariable(false),
            solo = reactiveVariable(false)
        )
    }
}