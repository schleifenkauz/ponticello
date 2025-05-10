package ponticello.model.obj

import javafx.scene.input.DataFormat
import ponticello.impl.Decimal
import ponticello.impl.asY
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.registry.reference
import ponticello.model.score.SynthObject
import ponticello.model.score.controls.BufferControl
import reaktive.value.ReactiveValue
import reaktive.value.now

sealed class BufferObject : AbstractSuperColliderObject() {
    abstract fun channels(): Int

    abstract fun frames(): Int

    abstract fun duration(): ReactiveValue<Decimal>

    fun createSynthObject(synthDef: SynthDefObject): SynthObject? {
        val controls = synthDef.getDefaultControls(null)
        val buf = controls.getOrNull("buf") ?: return null
        val control = buf.now as BufferControl
        control.sample.now = reference()
        val name = context[ScoreObjectRegistry].availableName(name.now)
        val obj = SynthObject.create(name, synthDef, controls)
        obj.setInitialSize(duration().now, 0.02.asY)
        return obj
    }

    companion object {
        val DATA_FORMAT = DataFormat("buffer")
    }
}