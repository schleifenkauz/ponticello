package ponticello.model.obj

import javafx.scene.input.DataFormat
import ponticello.impl.Decimal
import ponticello.impl.asY
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.registry.reference
import ponticello.model.score.SoundProcess
import ponticello.model.score.controls.BufferControl
import ponticello.sc.BufferControlSpec
import ponticello.sc.ControlSpec
import ponticello.sc.client.SuperColliderClient
import reaktive.value.ReactiveValue
import reaktive.value.now

sealed class BufferObject : AbstractSuperColliderObject() {
    abstract fun channels(): Int

    abstract fun frames(): Int

    abstract fun duration(): ReactiveValue<Decimal>

    fun createSynthObject(synthDef: InstrumentObject): SoundProcess? {
        val controls = synthDef.getDefaultControls(null)
        val buf = controls.getOrNull("buf") ?: return null
        val control = buf.now as BufferControl
        control.sample.now = reference()
        val name = context[ScoreObjectRegistry].availableName(name.now)
        val obj = SoundProcess.create(name, synthDef, controls)
        obj.setInitialSize(duration().now, 0.02.asY)
        return obj
    }

    fun plotBuffer() {
        val client = context[SuperColliderClient]
        client.run("${superColliderName}.plot('${name.now}')")
    }

    fun matches(spec: ControlSpec?) = spec is BufferControlSpec && channels() == spec.channels

    companion object {
        val DATA_FORMAT = DataFormat("buffer")
    }
}