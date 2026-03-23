package ponticello.model.flow

import hextant.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.copy
import ponticello.impl.writeCode
import ponticello.model.instr.BusObject
import ponticello.model.obj.BusReference
import ponticello.model.server.BusRegistry
import ponticello.sc.client.ScWriter
import reaktive.Observer
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.binding.flatMap
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
class LevelMeterFlow private constructor(val targetRef: ReactiveVariable<BusReference>) : AudioFlow() {
    override val active = reactiveVariable(true)

    override val isValid: ReactiveValue<Boolean> = targetRef.flatMap(BusReference::isResolved)

    @Transient
    private lateinit var targetObserver: Observer

    @SerialName("name")
    override var _name: ReactiveVariable<String>? = null

    @Transient
    var replyId: Int = -1

    override fun copy(): AudioFlow = LevelMeterFlow(targetRef.copy())

    override fun initialize(context: Context) {
        super.initialize(context)
        targetRef.now.resolve(context[BusRegistry])
        replyId = context[BusRegistry].reserveReplyId()
        targetObserver = targetRef.observe { _, _, _ -> sync() }
    }

    override fun ScWriter.createObject() {
    }

    override fun writeCode(placement: NodePlacement): String = writeCode {
        val target = targetRef.now.get() as? BusObject.AudioBus ?: return ""
        context[BusRegistry].createLevelSendSynth(writer, target, placement, replyId, superColliderName)
    }

    companion object {
        fun create(target: BusObject) = LevelMeterFlow(reactiveVariable(BusReference(target)))
    }
}