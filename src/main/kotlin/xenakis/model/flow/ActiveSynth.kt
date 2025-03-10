package xenakis.model.flow

import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.model.obj.BusObject
import xenakis.model.score.ObjectPosition
import xenakis.model.score.SynthObject
import xenakis.sc.client.ScWriter
import xenakis.sc.writeSynthCode

data class ActiveSynth(
    val obj: SynthObject,
    val absolutePosition: ObjectPosition,
    val suffix: Int
) : AudioNode {
    override val superColliderName = reactiveValue(
        when (suffix) {
            0 -> "~${obj.name.now}"
            else -> "~${obj.name.now}_$suffix"
        }
    )

    override fun ScWriter.writeCode(placement: NodePlacement) {
        val name = superColliderName.now.removePrefix("~")
        val synthVar = "~synths[$name]"
        val info = ScoreObjectInfo(absolutePosition, name, synthVar, placement)
        writeSynthCode(obj.synthDef, obj.context, info, obj.duration, obj.controls.controlMap)
    }

    override fun getInputs(): Collection<BusObject> = obj.getInputs()

    override fun getOutputs(): Collection<BusObject> = obj.getOutputs()

    override fun addListener(listener: AudioNode.Listener) {
        obj.controls.addListener(ControlsListener(obj, listener))
    }

    override fun toString(): String = superColliderName.now
}