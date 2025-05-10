package ponticello.model.flow

import hextant.context.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.*
import ponticello.model.obj.BusObject
import ponticello.model.obj.BusReference
import ponticello.model.obj.ParameterizedObjectDef
import ponticello.model.obj.ReferencedSynthDefObject
import ponticello.model.registry.BusRegistry
import ponticello.model.registry.reference
import ponticello.model.score.ParameterControlList
import ponticello.model.score.controls.BusControl
import ponticello.model.score.controls.ValueControl
import ponticello.model.score.controls.writeSynthCode
import reaktive.value.*
import reaktive.value.binding.and
import reaktive.value.binding.flatMap
import reaktive.value.binding.map

@Serializable
class SendFlow(
    val sourceRef: ReactiveVariable<BusReference>,
    val targetRef: ReactiveVariable<BusReference>,
    val amountPercent: ReactiveVariable<Decimal>,
) : ParameterizedAudioFlow() {
    @Transient
    lateinit var sourceBus: ReactiveValue<BusObject?>
        private set

    @Transient
    lateinit var targetBus: ReactiveValue<BusObject?>
        private set

    @Transient
    override val def: ParameterizedObjectDef = ReferencedSynthDefObject.get("send")

    @Transient
    override lateinit var controls: ParameterControlList
        private set

    @Transient
    override lateinit var isValid: ReactiveValue<Boolean>
        private set

    override fun initialize(context: Context) {
        super.initialize(context)
        targetRef.now.resolve(context[BusRegistry])
        targetBus = targetRef.map { ref -> ref.get() }
        sourceRef.now.resolve(context[BusRegistry])
        sourceBus = sourceRef.map { ref -> ref.get() }
        def.initialize(context)
        controls = ParameterControlList.create(
            "in" to BusControl(sourceRef),
            "out" to BusControl(targetRef),
            "amp" to ValueControl(reactiveVariable(amountPercent.now * 0.01.toDecimal())),
        )
        controls.initialize(context, this)
        isValid = sourceRef.flatMap(BusReference::isResolved) and targetRef.flatMap(BusReference::isResolved)
    }

    override fun copy(): AudioFlow =
        SendFlow(sourceRef.copy(), targetRef.copy(), amountPercent.copy())

    override fun writeCode(placement: NodePlacement): String = writeCode {
        writeSynthCode(this@SendFlow, superColliderName.removePrefix("~"), cutoff = zero, placement, latency = zero)
    }

    override fun getDefaultName(): ReactiveString =
        targetRef.flatMap { target -> target.name.map { name -> "Send to $name" } }

    companion object {
        fun create(bus: BusObject, target: BusObject, context: Context): SendFlow {
            val flow = SendFlow(
                reactiveVariable(bus.reference()),
                reactiveVariable(target.reference()),
                reactiveVariable(Decimal(100.0, 0))
            )
            flow.initialize(context)
            return flow
        }
    }
}