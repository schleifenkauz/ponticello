package xenakis.model.obj

import javafx.scene.input.DataFormat
import reaktive.value.ReactiveValue
import reaktive.value.now
import xenakis.impl.Decimal
import xenakis.impl.asY
import xenakis.model.registry.ScoreObjectRegistry
import xenakis.model.registry.reference
import xenakis.model.score.SynthObject
import xenakis.model.score.controls.BufferControl

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