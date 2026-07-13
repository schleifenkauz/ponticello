package ponticello.model.registry

import fxutils.prompt.PromptPlacement
import fxutils.prompt.SimpleSelectorPrompt
import ponticello.model.flow.MixerFlow
import ponticello.model.instr.BusObject
import ponticello.model.obj.NamedObject
import ponticello.model.project.PonticelloProject
import ponticello.model.project.flows
import reaktive.value.now

fun <O : NamedObject> O.reference(): ObjectReference<O> {
    return ObjectReference(this)
}

private sealed interface AssociatedMixerChoice {
    data class Choose(val mixer: MixerFlow) : AssociatedMixerChoice {
        override fun toString(): String = mixer.name.now
    }

    data object None : AssociatedMixerChoice {
        override fun toString(): String = "none"
    }
}

fun BusObject.chooseTargetMixer(project: PonticelloProject, promptPlacement: PromptPlacement): BusObject = also {
    val mixers = project.flows.allFlows().filterIsInstance<MixerFlow>()
    val choices = listOf(AssociatedMixerChoice.None) + mixers.map(AssociatedMixerChoice::Choose)
    if (mixers.isNotEmpty()) {
        val selector = SimpleSelectorPrompt(choices, "Add bus to mixer?")
        val choice = selector.showDialog(promptPlacement)
        if (choice is AssociatedMixerChoice.Choose) {
            choice.mixer.addSource(this)
        }
    }
}