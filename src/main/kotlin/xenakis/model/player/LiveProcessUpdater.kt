package xenakis.model.player

import xenakis.impl.Decimal
import xenakis.model.obj.BufferReference
import xenakis.model.obj.BusReference
import xenakis.model.obj.GlobalPatternReference
import xenakis.model.obj.ParameterizedObject
import xenakis.model.score.Envelope
import xenakis.model.score.controls.ParameterControl
import xenakis.sc.NumericalControlSpec
import xenakis.sc.client.ScWriter

class LiveProcessUpdater(obj: ParameterizedObject): AbstractLiveUpdater(obj) {
    override fun ScWriter.updateValue(
        uniqueName: String,
        parameter: String,
        value: Decimal,
        onBus: Boolean,
        remap: Boolean
    ) {
        updateArgument(uniqueName, parameter, value.toString())
    }

    override fun ScWriter.updateBus(uniqueName: String, parameter: String, bus: BusReference) {
        val superColliderName = bus.get()?.superColliderName ?: return
        updateArgument(uniqueName, parameter, superColliderName)
    }

    override fun ScWriter.updateValueBus(uniqueName: String, parameter: String, bus: BusReference) {
        val superColliderName = bus.get()?.superColliderName ?: return
        updateArgument(uniqueName, parameter, superColliderName)
    }

    override fun ScWriter.remapBus(uniqueName: String, parameter: String, bus: BusReference) {
        val superColliderName = bus.get()?.superColliderName ?: return
        updateArgument(uniqueName, parameter, superColliderName)
    }

    override fun ScWriter.updateBuffer(uniqueName: String, parameter: String, buf: BufferReference) {
        val superColliderName = buf.get()?.superColliderName ?: return
        updateArgument(uniqueName, parameter, superColliderName)
    }

    override fun updateEnvelope(
        writer: ScWriter,
        objectTime: Decimal,
        uniqueName: String,
        parameter: String,
        envelope: Envelope,
        remap: Boolean
    ) {
        val spec = obj.getSpec(parameter) as? NumericalControlSpec ?: return
        val envelopeCode = envelope.code(spec.warp)
        writer.updateArgument(uniqueName, parameter, envelopeCode)
    }

    override fun ScWriter.updatePattern(uniqueName: String, parameter: String, pattern: GlobalPatternReference) {
        val superColliderName = pattern.get()?.superColliderName ?: return
        updateArgument(uniqueName, parameter, superColliderName)
    }

    private fun ScWriter.updateArgument(uniqueName: String, parameter: String, expr: String) {
        +"${ParameterControl.uniqueArgumentName(uniqueName, parameter)} = $expr"
    }
}