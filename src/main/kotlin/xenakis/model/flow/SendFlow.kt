package xenakis.model.flow

import hextant.context.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.Observer
import reaktive.value.*
import reaktive.value.binding.flatMap
import reaktive.value.binding.map
import xenakis.impl.*
import xenakis.model.obj.BusObject
import xenakis.model.obj.BusReference
import xenakis.model.obj.ParameterizedObjectDef
import xenakis.model.obj.ReferencedSynthDefObject
import xenakis.model.registry.BusRegistry
import xenakis.model.registry.reference
import xenakis.model.score.ParameterControlList
import xenakis.model.score.controls.BusControl
import xenakis.model.score.controls.ValueControl
import xenakis.model.score.controls.writeSynthCode
import xenakis.sc.client.ScWriter

@Serializable
class SendFlow(
    val targetRef: ReactiveVariable<BusReference>,
    val amountPercent: ReactiveVariable<Decimal>,
) : ParameterizedAudioFlow() {
    @Transient
    private val observers = mutableListOf<Observer>()

    @Transient
    lateinit var targetBus: ReactiveValue<BusObject?>
        private set

    @Transient
    override val def: ParameterizedObjectDef = ReferencedSynthDefObject.get("send")

    @Transient
    override lateinit var controls: ParameterControlList
        private set

    override fun initialize(context: Context, bus: BusObject) {
        super.initialize(context, bus)
        targetRef.now.resolve(context[BusRegistry])
        targetBus = targetRef.map { ref -> ref.get() }
        def.initialize(context)
        controls = ParameterControlList.create(
            "in" to BusControl(reactiveVariable(associatedBus.reference())),
            "out" to BusControl(reactiveVariable(targetRef.now)),
            "amp" to ValueControl(reactiveVariable(amountPercent.now * 0.01.toDecimal())),
        )
        controls.initialize(context, this)
    }

    override fun copy(): AudioFlow =
        SendFlow(targetRef.copy(), amountPercent.copy())

    override fun validate(): Boolean {
        if (!targetRef.now.isResolved.now) {
            Logger.error("Target bus ${targetRef.now.getName()} is not resolved")
            return false
        }
        return controls.validate()
    }

    override fun ScWriter.writeCode(placement: NodePlacement) {
        writeSynthCode(
            this@SendFlow, superColliderName.now.removePrefix("~"),
            cutoff = zero, placement, latency = zero,
            customSynthVar = superColliderName.now,
        )
    }

    override fun getDefaultName(): ReactiveString =
        targetRef.flatMap { target -> target.name.map { name -> "Send to $name" } }

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