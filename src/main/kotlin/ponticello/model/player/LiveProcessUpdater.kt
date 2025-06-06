package ponticello.model.player

import ponticello.impl.Decimal
import ponticello.model.obj.BufferReference
import ponticello.model.obj.BusReference
import ponticello.model.obj.ParameterizedObject
import ponticello.model.score.Envelope
import ponticello.model.score.controls.ParameterControl
import ponticello.sc.NumericalControlSpec
import ponticello.sc.ScExpr
import ponticello.sc.client.ScWriter
import ponticello.sc.code

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

    override fun ScWriter.updateExprControl(uniqueName: String, parameter: String, expr: ScExpr) {
        updateArgument(uniqueName, parameter, expr.code(obj.context))
    }

    private fun ScWriter.updateArgument(uniqueName: String, parameter: String, expr: String) {
        +"${ParameterControl.uniqueArgumentName(uniqueName, parameter)} = $expr"
    }
}