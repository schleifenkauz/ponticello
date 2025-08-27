package ponticello.model.flow

import hextant.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.*
import ponticello.model.obj.BusObject
import ponticello.model.obj.BusReference
import ponticello.model.obj.InstrumentObject
import ponticello.model.obj.ReferencedSynthDefObject
import ponticello.model.registry.BusRegistry
import ponticello.model.registry.reference
import ponticello.model.score.ParameterControlList
import ponticello.model.score.controls.BusControl
import ponticello.model.score.controls.ValueControl
import ponticello.model.score.controls.writeSynthCode
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.binding.and
import reaktive.value.binding.flatMap
import reaktive.value.binding.map
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
@SerialName("SendFlow")
class SendFlow(
    val sourceRef: ReactiveVariable<BusReference>,
    val targetRef: ReactiveVariable<BusReference>,
    val amountPercent: ReactiveVariable<Decimal>,
) : ParameterizedAudioFlow() {
    @SerialName("name")
    override var _name: ReactiveVariable<String>? = null

    @Transient
    lateinit var sourceBus: ReactiveValue<BusObject?>
        private set

    @Transient
    lateinit var targetBus: ReactiveValue<BusObject?>
        private set

    @Transient
    override val def: InstrumentObject = ReferencedSynthDefObject.get("send")

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
        val latency = zero // context[Settings].serverLatency.now
        writeSynthCode(
            this@SendFlow, superColliderName.removePrefix("~"),
            cutoff = zero, placement, latency, run = isActive.now
        )
    }

    companion object {
        fun create(source: BusObject, target: BusObject): SendFlow = SendFlow(
            reactiveVariable(source.reference()),
            reactiveVariable(target.reference()),
            reactiveVariable(Decimal(100.0, 0))
        )
    }
}