package xenakis.model.player

import javafx.geometry.HorizontalDirection
import xenakis.impl.Decimal
import xenakis.impl.Logger
import xenakis.model.obj.BufferReference
import xenakis.model.obj.BusReference
import xenakis.model.obj.GlobalPatternReference
import xenakis.model.obj.ParameterizedObject
import xenakis.model.score.Envelope
import xenakis.model.score.controls.EnvelopeControl
import xenakis.model.score.controls.ParameterControl
import xenakis.sc.NumericalControlSpec
import xenakis.sc.ScExpr
import xenakis.sc.client.ScWriter

class LiveSynthUpdater(obj: ParameterizedObject) : AbstractLiveUpdater(obj) {
    override fun ScWriter.updateValue(uniqueName: String, parameter: String, value: Decimal) {
        val varName = ParameterControl.uniqueArgumentName(uniqueName, parameter)
        +"$varName.set($value)"
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

    override fun updateUGenControl(writer: ScWriter, uniqueName: String, parameter: String, expr: ScExpr) {
        super.updateUGenControl(writer, uniqueName, parameter, expr)
        val busName = ParameterControl.uniqueArgumentName(uniqueName, parameter)
        writer.appendLine("${obj.superColliderPrefix}$uniqueName.map('$parameter', $busName);")
    }

    override fun updateEnvelope(
        writer: ScWriter, objectTime: Decimal,
        uniqueName: String, parameter: String,
        envelope: Envelope,
    ) {
        val spec = obj.getSpec(parameter) as? NumericalControlSpec ?: return
        val cut = envelope.cut(objectTime, HorizontalDirection.RIGHT, spec.warp)
        val envelopeCode = cut.code(spec.warp)
        val auxiliarySynthName = EnvelopeControl.envSynthName(uniqueName, parameter)
        val auxiliaryBus = ParameterControl.uniqueArgumentName(uniqueName, parameter)
        writer.appendLine("$auxiliarySynthName = { $envelopeCode.kr }.play($auxiliarySynthName, $auxiliaryBus, fadeTime: 0.02, addAction: 'addReplace');")
        val busName = ParameterControl.uniqueArgumentName(uniqueName, parameter)
        writer.appendLine("${obj.superColliderPrefix}$uniqueName.map('$parameter', $busName);")
    }
}