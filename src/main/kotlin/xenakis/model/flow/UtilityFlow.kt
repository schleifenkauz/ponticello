package xenakis.model.flow

import hextant.context.Context
import kotlinx.serialization.Serializable
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
import xenakis.model.score.ObjectPosition
import xenakis.model.score.ParameterControls
import xenakis.sc.client.ScWriter
import xenakis.sc.writeSynthCode

@Serializable
class UtilityFlow(
    private val busRef: ObjectReference,
    private val volumeDb: ReactiveVariable<Decimal>,
    private val muted: ReactiveVariable<Boolean>,
    val solo: ReactiveVariable<Boolean>,
) : AudioFlow() {
    override lateinit var associatedBus: BusObject
        private set

    override fun copyFor(associatedBus: BusObject): AudioFlow =
        UtilityFlow(associatedBus.reference(), volumeDb.copy(), muted.copy(), solo.copy())

    override lateinit var superColliderName: ReactiveString
        private set

    override fun initialize(context: Context) {
        super.initialize(context)
        busRef.resolve(context[BusRegistry])
        associatedBus = busRef.get()
        superColliderName = associatedBus.name.map { name -> "~flow_${name}_utility" }
    }

    override fun ScWriter.writeCode(placement: NodePlacement) {
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
        val synthVar = superColliderName.now
        val info = ScoreObjectInfo(ObjectPosition.ZERO, synthVar.removePrefix("~"), synthVar, placement)
        writeSynthCode(
            ReferencedSynthDefObject.get("utility"), controls,
            context, info, duration = null
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