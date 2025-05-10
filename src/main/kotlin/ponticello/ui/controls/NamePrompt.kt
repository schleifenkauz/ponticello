package ponticello.ui.controls

import fxutils.prompt.TextPrompt
import ponticello.model.registry.ObjectRegistry
import ponticello.sc.Identifier

class NamePrompt(
    private val registry: ObjectRegistry<*>, title: String, initialName: String
) : TextPrompt<String>(title, initialName) {
    override fun convert(text: String): String? {
        if (!Identifier.isValid(text)) return null
        if (registry.has(text)) return null
        return text
    }
}