package ponticello.sc.editor

import fxutils.prompt.PromptPlacement
import ponticello.model.player.MeterRegistry
import ponticello.model.score.MeterObject
import ponticello.ui.registry.MeterSelectorPrompt

class MeterSelector : ObjectSelector<MeterObject>() {
    override fun getOptions(): List<MeterObject> = context[MeterRegistry]

    override fun createNewObject(name: String, promptPlacement: PromptPlacement): MeterObject? =
        MeterSelectorPrompt.MeterConfigDialog(MeterObject.createDefault(), name).showDialog(promptPlacement)
}