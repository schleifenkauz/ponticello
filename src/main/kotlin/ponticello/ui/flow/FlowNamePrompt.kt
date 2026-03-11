package ponticello.ui.flow

import fxutils.prompt.TextPrompt
import ponticello.sc.Identifier

class FlowNamePrompt(
    private val takenFlowNames: Set<String>,
    title: String, initialText: String,
) : TextPrompt<String>(title, initialText) {
    override fun convert(text: String): String? =
        text.takeIf { Identifier.Companion.isValid(it) && it !in takenFlowNames }
}