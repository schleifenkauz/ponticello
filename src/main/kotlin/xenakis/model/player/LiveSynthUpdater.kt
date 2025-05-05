package xenakis.model.player

import javafx.geometry.HorizontalDirection
import xenakis.impl.Decimal
import xenakis.impl.Logger
import xenakis.model.obj.BufferReference
import xenakis.model.obj.BusReference
import xenakis.model.obj.GlobalPatternReference
import xenakis.model.obj.ParameterizedObject
import xenakis.model.score.Envelope
import xenakis.model.score.controls.ParameterControl
import xenakis.model.score.controls.guardAgainstReplaceNil
import xenakis.sc.NumericalControlSpec
import xenakis.sc.ScExpr
import xenakis.sc.client.ScWriter

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
        allocateBus: Boolean, currentValue: Decimal
    ) {
        val busName = ParameterControl.auxilBusName(uniqueName, parameter)
        if (allocateBus) {
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

    override fun ScWriter.updatePattern(uniqueName: String, parameter: String, pattern: GlobalPatternReference) {
        Logger.warn("Cannot update pattern for synth object $uniqueName", Logger.Category.Playback)
    }

    override fun updateUGenControl(
        writer: ScWriter, uniqueName: String, parameter: String,
        expr: ScExpr, replace: Boolean, remap: Boolean,
    ) {
        super.updateUGenControl(writer, uniqueName, parameter, expr, replace, remap)
        if (remap) writer.remap(uniqueName, parameter)
    }

    override fun updateEnvelope(
        writer: ScWriter, objectTime: Decimal,
        uniqueName: String, parameter: String,
        envelope: Envelope, remap: Boolean,
    ) {
        val spec = obj.getSpec(parameter) as? NumericalControlSpec ?: return
        val cut = envelope.cut(objectTime, HorizontalDirection.RIGHT, spec.warp)
        val envelopeCode = cut.code(spec.warp)
        val auxiliarySynthName = ParameterControl.auxilSynthName(uniqueName, parameter)
        val placement = getAuxiliarySynthPlacement(parameter, uniqueName, replace = true)
        val action = guardAgainstReplaceNil(placement)
        val auxiliaryBus = ParameterControl.auxilBusName(uniqueName, parameter)
        writer.appendLine(
            "$auxiliarySynthName = { $envelopeCode.kr }" +
                    ".play(target: ${placement.target}, outpus: $auxiliaryBus, fadeTime: 0.02, addAction: ${action});"
        )
        if (remap) writer.remap(uniqueName, parameter)
    }
}