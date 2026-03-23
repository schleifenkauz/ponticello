package ponticello.model.flow

import hextant.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.Decimal
import ponticello.impl.copy
import ponticello.impl.toDecimal
import ponticello.model.instr.BusObject
import ponticello.model.instr.InstrumentObject
import ponticello.model.instr.ReferencedSynthDefObject
import ponticello.model.obj.BusReference
import ponticello.model.registry.reference
import ponticello.model.score.controls.BusControl
import ponticello.model.score.controls.ParameterControlList
import ponticello.model.score.controls.ValueControl
import ponticello.model.server.BusRegistry
import reaktive.Reactive
import reaktive.value.*
import reaktive.value.binding.and
import reaktive.value.binding.flatMap
import reaktive.value.binding.map

@Serializable
@SerialName("SendFlow")
class SendFlow(
    val sourceRef: ReactiveVariable<BusReference>,
    val targetRef: ReactiveVariable<BusReference>,
    val amountPercent: ReactiveVariable<Decimal>,
) : ParameterizedAudioFlow() {
    override val active = reactiveVariable(true)

    @SerialName("name")
    override var _name: ReactiveVariable<String>? = null

    @Transient
    lateinit var sourceBus: ReactiveValue<BusObject?>
        private set

    @Transient
    lateinit var targetBus: ReactiveValue<BusObject?>
        private set

    override val instrumentChanged: Reactive
        get() = reactiveValue(Unit)

    override fun getInstrument(): InstrumentObject = ReferencedSynthDefObject.get("send")

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
        getInstrument().initialize(context)
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

    companion object {
        fun create(source: BusObject, target: BusObject): SendFlow = SendFlow(
            reactiveVariable(source.reference()),
            reactiveVariable(target.reference()),
            reactiveVariable(Decimal(100.0, 0))
        )
    }
}