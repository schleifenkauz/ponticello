package xenakis.model.flow

import hextant.context.Context
import kotlinx.serialization.Serializable
import reaktive.value.*
import reaktive.value.binding.binding
import reaktive.value.binding.flatMap
import reaktive.value.binding.map
import xenakis.impl.Decimal
import xenakis.impl.copy
import xenakis.impl.toDecimal
import xenakis.model.obj.BusObject
import xenakis.model.obj.ReferencedSynthDefObject
import xenakis.model.registry.BusRegistry
import xenakis.model.registry.ObjectReference
import xenakis.model.score.BusControl
import xenakis.model.score.ConstantControl
import xenakis.model.score.ParameterControls
import xenakis.sc.client.ScWriter
import xenakis.sc.writeSynthCode

@Serializable
class SendFlow(
    val busRef: ObjectReference,
    val targetRef: ReactiveVariable<ObjectReference>,
    val amountPercent: ReactiveVariable<Decimal>,
) : AudioFlow() {
    lateinit var targetBus: ReactiveValue<BusObject>
        private set

    override lateinit var associatedBus: BusObject
        private set

    override lateinit var superColliderName: ReactiveString
        private set

    override fun initialize(context: Context) {
        super.initialize(context)
        targetRef.now.resolve(context[BusRegistry])
        targetBus = targetRef.map { ref -> ref.get() }
        busRef.resolve(context[BusRegistry])
        associatedBus = busRef.get()
        superColliderName =
            targetBus.flatMap { target -> binding(associatedBus.name, target.name) { a, t -> "~send_${a}_${t}" } }
    }

    override fun copyFor(associatedBus: BusObject): AudioFlow =
        SendFlow(associatedBus.reference(), targetRef.copy(), amountPercent.copy())

    override fun ScWriter.writeCode(synthName: String, order: ScoreObjectInfo) {
        val controls = ParameterControls(
            mutableMapOf(
                "in" to BusControl(reactiveVariable(busRef)),
                "out" to BusControl(reactiveVariable(targetRef.now)),
                "volume" to ConstantControl(reactiveVariable(amountPercent.now * 0.01.toDecimal())),
            ),
        )
        writeSynthCode(
            synthName, ReferencedSynthDefObject.get("send"), controls,
            context, order, duration = null
        )
    }

    override fun getConnectedBusses(vararg flowType: FlowType): Set<BusObject> = when {
        FlowType.In in flowType -> setOf(associatedBus)
        FlowType.Out in flowType -> setOf(targetBus.now)
        else -> emptySet()
    }

    companion object {
        fun createFor(bus: BusObject, target: BusObject) =
            SendFlow(
                bus.reference(),
                reactiveVariable(target.reference()),
                reactiveVariable(Decimal(100.0, 0))
            )
    }
}