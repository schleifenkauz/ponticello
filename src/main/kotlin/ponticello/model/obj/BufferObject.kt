package ponticello.model.obj

import fxutils.drag.TypedDataFormat
import kotlinx.serialization.Serializable
import ponticello.impl.Decimal
import ponticello.impl.toDecimal
import ponticello.model.project.PLAYBACK_SETTINGS
import ponticello.model.project.get
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.registry.reference
import ponticello.model.score.ParameterControlList
import ponticello.model.score.SoundProcess
import ponticello.model.score.controls.BufferControl
import ponticello.model.score.controls.BusControl
import ponticello.sc.BufferControlSpec
import ponticello.sc.ControlSpec
import ponticello.sc.client.SuperColliderClient
import reaktive.value.ReactiveValue
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
sealed class BufferObject : AbstractSuperColliderObject() {
    abstract fun channels(): Int

    abstract fun frames(): Int

    abstract fun duration(): ReactiveValue<Decimal>

    protected open fun waitForInfos() {}

    fun createSynthObject(synthDef: InstrumentObject): SoundProcess? {
        waitForInfos()
        val controls = synthDef.getDefaultControls(null)
        val buf = controls["buf"] as? BufferControl ?: return null
        buf.sample.now = reference()
        val name = context[ScoreObjectRegistry].availableName(name.now)
        val instrument = InstrumentReference.UserDefined(synthDef.reference())
        val djMode = context.project[PLAYBACK_SETTINGS].djMode
        if (synthDef.hasParameter("out") && djMode.activated.now && djMode.selectedBus != null) {
            controls["out"] = BusControl(reactiveVariable(djMode.selectedBus!!))
        }
        val obj = SoundProcess.create(name, instrument, ParameterControlList.from(controls))
        val initialHeight = if (djMode.activated.now) 0.1 else 0.02
        obj.setInitialSize(duration().now, initialHeight.toDecimal())
        return obj
    }

    fun plotBuffer() {
        val client = context[SuperColliderClient]
        client.run("${superColliderName}.plot('${name.now}')")
    }

    fun matches(spec: ControlSpec?) = spec is BufferControlSpec && channels() == spec.channels

    companion object {
        val DATA_FORMAT = TypedDataFormat<BufferReference>("buffer")
    }
}