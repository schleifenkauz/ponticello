package ponticello.ui.flow

import fxutils.prompt.SimpleSelectorPrompt
import hextant.context.Context
import ponticello.model.flow.AudioFlows
import ponticello.model.flow.VSTPluginFlow
import ponticello.model.obj.VSTPluginReference

class VSTInstrumentSelectorPrompt(private val context: Context) :
    SimpleSelectorPrompt<VSTPluginReference>("Select VST") {
    override fun extractText(option: VSTPluginReference): String = option.getName()

    override fun options(): List<VSTPluginReference> =
        context[AudioFlows].allFlows()
            .filterIsInstance<VSTPluginFlow>()
            .filter(VSTPluginFlow::supportsMidiInput)
            .map(::VSTPluginReference)
}