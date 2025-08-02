package ponticello.model.player

import ponticello.impl.Decimal
import ponticello.model.obj.BufferReference
import ponticello.model.obj.BusReference
import ponticello.model.obj.ParameterizedObject
import ponticello.model.score.controls.EnvelopeControl
import ponticello.model.score.controls.ParameterControl
import ponticello.sc.ScExpr
import ponticello.sc.client.ScWriter

class LiveSynthUpdater(obj: ParameterizedObject) : AbstractLiveUpdater(obj) {
    override fun ScWriter.updateValue(
        uniqueName: String, parameter: String, value: Decimal,
        onBus: Boolean, remap: Boolean,
    ) {
        if (onBus) {
            mapToValueBus(uniqueName, parameter, value, remap)
        } else {
            setSynthArg(uniqueName, parameter, value)
        }
    }

    private fun ScWriter.setSynthArg(uniqueName: String, parameter: String, value: Decimal) {
        val synthName = "${obj.superColliderPrefix}$uniqueName"
        +"$synthName.set(\\$parameter, $value)"
    }

    private fun ScWriter.mapToValueBus(
        uniqueName: String,
        parameter: String,
        value: Decimal,
        remap: Boolean,
    ) {
        val busName = ParameterControl.auxilBusName(uniqueName, parameter)
        +"$busName.set($value)"
        if (remap) remap(uniqueName, parameter)
    }

    override fun ScWriter.updateValueControlMode(
        uniqueName: String, parameter: String,
        allocateBus: Boolean, currentValue: Decimal,
    ) {
        val busName = ParameterControl.auxilBusName(uniqueName, parameter)
        if (allocateBus) {
            +"$busName = Bus.control(s, 1)"
            mapToValueBus(uniqueName, parameter, currentValue, remap = true)
        } else {
            +"$busName.free"
            +"$busName = nil"
            setSynthArg(uniqueName, parameter, currentValue)
        }
    }

    private fun ScWriter.remap(uniqueName: String, parameter: String) {
        val busName = ParameterControl.auxilBusName(uniqueName, parameter)
        val synthVar = "${obj.superColliderPrefix}$uniqueName"
        +"$synthVar.map(\\$parameter, $busName)"
    }

    override fun ScWriter.remapBus(uniqueName: String, parameter: String, bus: BusReference) {
        val superColliderName = bus.get()?.superColliderName ?: return
        +"${obj.superColliderPrefix}$uniqueName.map('$parameter', $superColliderName)"
    }

    override fun ScWriter.updateBus(uniqueName: String, parameter: String, bus: BusReference) {
        val superColliderName = bus.get()?.superColliderName ?: return
        +"${obj.superColliderPrefix}$uniqueName.set('$parameter', $superColliderName)"
    }

    override fun ScWriter.updateValueBus(uniqueName: String, parameter: String, bus: BusReference) {
        val superColliderName = bus.get()?.superColliderName ?: return
        +"${obj.superColliderPrefix}$uniqueName.set('$parameter', $superColliderName.getSynchronous)"
    }

    override fun ScWriter.updateBuffer(uniqueName: String, parameter: String, buf: BufferReference) {
        val superColliderName = buf.get()?.superColliderName ?: return
        +"${obj.superColliderPrefix}$uniqueName.set('$parameter', $superColliderName)"
    }

    override fun updateUGenControl(
        writer: ScWriter, uniqueName: String, parameter: String,
        expr: ScExpr, replace: Boolean, remap: Boolean, objectTime: Decimal,
    ) {
        super.updateUGenControl(writer, uniqueName, parameter, expr, replace, remap, objectTime)
        if (remap) writer.remap(uniqueName, parameter)
    }

    override fun ScWriter.updateExprControl(uniqueName: String, parameter: String, expr: ScExpr) {
        append("${obj.superColliderPrefix}$uniqueName.set('$parameter', ")
        expr.code(writer, obj.context)
        appendLine(");")
    }

    override fun updateEnvelope(
        writer: ScWriter, objectTime: Decimal,
        uniqueName: String, parameter: String,
        envelope: EnvelopeControl, remap: Boolean, replaceAuxilSynth: Boolean,
    ) {
        envelope.synthDefSynchronizerJob.join()
        val auxilVarName = ParameterControl.auxilBusName(uniqueName, parameter)
        val auxilSynthName = ParameterControl.auxilSynthName(uniqueName, parameter)
        val placement = getAuxiliarySynthPlacement(parameter, uniqueName, replaceAuxilSynth)
        envelope.createEnvelopeSynth(
            writer, auxilVarName, auxilSynthName, placement,
            cutoff = objectTime, paused = false
        )
        if (remap) writer.remap(uniqueName, parameter)
    }
}