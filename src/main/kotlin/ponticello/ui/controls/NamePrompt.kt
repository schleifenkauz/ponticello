package ponticello.ui.controls

import fxutils.prompt.TextPrompt
import javafx.scene.input.KeyEvent
import ponticello.model.registry.NamedObjectList
import ponticello.sc.Identifier

class NamePrompt(
    private val registry: NamedObjectList<*>, title: String, initialName: String
) : TextPrompt<String>(title, initialName) {
    override suspend fun convert(text: String, ev: KeyEvent): String? {
        if (!Identifier.isValid(text)) return null
        if (registry.has(text)) return null
        return text
    }
}