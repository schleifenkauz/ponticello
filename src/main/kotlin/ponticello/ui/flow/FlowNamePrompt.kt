package ponticello.ui.flow

import fxutils.prompt.TextPrompt
import javafx.scene.input.KeyEvent
import ponticello.sc.Identifier

class FlowNamePrompt(
    private val takenFlowNames: Set<String>,
    title: String, initialText: String,
) : TextPrompt<String>(title, initialText) {
    override suspend fun convert(text: String, ev: KeyEvent): String? =
        text.takeIf { Identifier.isValid(it) && it !in takenFlowNames }
}