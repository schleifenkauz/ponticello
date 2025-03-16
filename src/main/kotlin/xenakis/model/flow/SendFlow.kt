package xenakis.model.flow

import hextant.context.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.Observer
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.binding.map
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.Decimal
import xenakis.impl.copy
import xenakis.impl.toDecimal
import xenakis.model.obj.BusObject
import xenakis.model.obj.BusReference
import xenakis.model.obj.ReferencedSynthDefObject
import xenakis.model.registry.BusRegistry
import xenakis.model.registry.ObjectReference
import xenakis.model.registry.reference
import xenakis.model.score.BusControl
import xenakis.model.score.ConstantControl
import xenakis.model.score.ObjectPosition
import xenakis.model.score.ParameterControls
import xenakis.sc.client.ScWriter
import xenakis.sc.writeSynthCode

@Serializable
class SendFlow(
    val targetRef: ReactiveVariable<BusReference>,
    val amountPercent: ReactiveVariable<Decimal>,
) : AudioFlow() {
    @Transient
    private val observers = mutableListOf<Observer>()

    @Transient
    lateinit var targetBus: ReactiveValue<BusObject?>
        private set

    override fun initialize(context: Context, bus: BusObject) {
        super.initialize(context, bus)
        targetRef.now.resolve(context[BusRegistry])
        targetBus = targetRef.map { ref -> ref.get() }
    }

    override fun copy(): AudioFlow =
        SendFlow(targetRef.copy(), amountPercent.copy())

    override fun ScWriter.writeCode(placement: NodePlacement) {
        val controls = ParameterControls(
            mutableMapOf(
                "in" to BusControl(reactiveVariable(associatedBus.reference())),
                "out" to BusControl(reactiveVariable(targetRef.now)),
                "amp" to ConstantControl(reactiveVariable(amountPercent.now * 0.01.toDecimal())),
            ),
        )
        val synthVar = superColliderName.now
        val info = ScoreObjectInfo(ObjectPosition.ZERO, synthVar.removePrefix("~"), synthVar, placement)
        writeSynthCode(
            ReferencedSynthDefObject.get("send"), context,
            info, duration = null, controls.controlMap
        )
    }

    override fun getDefaultName(): String = "Send ${associatedBus.name.now} to ${targetRef.now.getName()}"

    override fun getInputs(): Collection<BusObject> = setOf(associatedBus)

    override fun getOutputs(): Collection<BusObject> = targetBus.now?.let(::setOf).orEmpty()

    override fun addListener(listener: AudioNode.Listener) {
        val obs = targetBus.observe { _, old, new ->
            if (old != null) listener.removedBus(old, FlowType.Out)
            if (new != null) listener.addedBus(new, FlowType.Out)
        }
        observers.add(obs)
    }

    companion object {
        fun createFor(bus: BusObject, target: BusObject, context: Context): SendFlow {
            val flow = SendFlow(
                reactiveVariable(target.reference()),
                reactiveVariable(Decimal(100.0, 0))
            )
            flow.initialize(context, bus)
            return flow
        }
    }
}