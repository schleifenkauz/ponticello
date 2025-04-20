package xenakis.model.player

import javafx.geometry.HorizontalDirection
import xenakis.impl.Decimal
import xenakis.impl.Logger
import xenakis.model.flow.AudioFlow
import xenakis.model.flow.NodePlacement
import xenakis.model.obj.BufferReference
import xenakis.model.obj.BusReference
import xenakis.model.obj.GlobalPatternReference
import xenakis.model.obj.ParameterizedObject
import xenakis.model.score.Envelope
import xenakis.model.score.SynthObject
import xenakis.model.score.controls.EnvelopeControl
import xenakis.model.score.controls.ParameterControl
import xenakis.sc.NumericalControlSpec
import xenakis.sc.ScExpr
import xenakis.sc.client.ScWriter

class LiveSynthUpdater(obj: ParameterizedObject) : AbstractLiveUpdater(obj) {
    override fun ScWriter.updatedDefinition() {
        val playback = obj.context[PlaybackManager]
        runOnActiveObjects { uniqueName, objectTime ->
            val synthName = "synth_$uniqueName"
            val placement = NodePlacement(NodePlacement.AddAction.AddReplace, target = synthName)
            when (obj) {
                is AudioFlow -> obj.run { writeCode(placement) }
                is SynthObject -> obj.run { writeCode(uniqueName, placement, cutoff = objectTime) }
            }
        }
    }

    override fun ScWriter.updateValue(uniqueName: String, parameter: String, value: Decimal) {
        +"~synth_$uniqueName.set('$parameter', $value)"
    }

    override fun ScWriter.remapBus(uniqueName: String, parameter: String, bus: BusReference) {
        val superColliderName = bus.get()?.superColliderName ?: return
        +"~synth_$uniqueName.map('$parameter', $superColliderName)"
    }

    override fun ScWriter.updateBus(uniqueName: String, parameter: String, bus: BusReference) {
        val superColliderName = bus.get()?.superColliderName ?: return
        +"~synth_$uniqueName.set('$parameter', $superColliderName)"
    }

    override fun ScWriter.updateValueBus(uniqueName: String, parameter: String, bus: BusReference) {
        val superColliderName = bus.get()?.superColliderName ?: return
        +"~synth_$uniqueName.set('$parameter', $superColliderName.getSynchronous)"
    }

    override fun ScWriter.updateBuffer(uniqueName: String, parameter: String, buf: BufferReference) {
        val superColliderName = buf.get()?.superColliderName ?: return
        +"~synth_$uniqueName.set('$parameter', $superColliderName)"
    }

    override fun ScWriter.updatePattern(uniqueName: String, parameter: String, pattern: GlobalPatternReference) {
        Logger.warn("Cannot update pattern for synth object $uniqueName", Logger.Category.Playback)
    }

    override fun updateUGenControl(writer: ScWriter, uniqueName: String, parameter: String, expr: ScExpr) {
        super.updateUGenControl(writer, uniqueName, parameter, expr)
        val busName = ParameterControl.uniqueArgumentName(uniqueName, parameter)
        writer.appendLine("~synth_$uniqueName.map('$parameter', $busName);")
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
        writer.appendLine("$auxiliarySynthName.free;")
        val auxiliaryBus = ParameterControl.uniqueArgumentName(uniqueName, parameter)
        writer.appendLine("$auxiliarySynthName = { $envelopeCode.kr }.play(s, $auxiliaryBus);")
        val busName = ParameterControl.uniqueArgumentName(uniqueName, parameter)
        writer.appendLine("~synth_$uniqueName.map('$parameter', $busName);")
    }
}