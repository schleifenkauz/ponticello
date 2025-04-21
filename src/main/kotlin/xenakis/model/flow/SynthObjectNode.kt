package xenakis.model.flow

import reaktive.value.binding.map
import reaktive.value.now
import xenakis.model.obj.BusObject
import xenakis.model.player.ActiveObjectManager
import xenakis.model.score.ObjectPosition
import xenakis.model.score.SynthObject
import xenakis.sc.client.ScWriter

data class SynthObjectNode(
    val obj: SynthObject,
    val absolutePosition: ObjectPosition,
    val suffix: Int
) : AudioNode {
    override val superColliderName = obj.name.map { name -> "~synth_${ActiveObjectManager.uniqueName(name, suffix)}" }

    override fun validate(): Boolean = obj.validate()

    override fun ScWriter.writeCode(placement: NodePlacement) {
        throw UnsupportedOperationException("ActiveSynths are not meant to be written to SC")
    }

    override fun getInputs(): Collection<BusObject> = obj.getInputs()

    override fun getOutputs(): Collection<BusObject> = obj.getOutputs()

    override fun addListener(listener: AudioNode.Listener) {
        obj.controls.addListener(AudioNodeBusControlsListener(listener))
    }

    override fun toString(): String = superColliderName.now
}