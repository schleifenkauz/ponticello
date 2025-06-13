package ponticello.ui.controls

import fxutils.prompt.TextPrompt
import ponticello.model.registry.NamedObjectList
import ponticello.sc.Identifier

class NamePrompt(
    private val registry: NamedObjectList<*>, title: String, initialName: String
) : TextPrompt<String>(title, initialName) {
    override fun convert(text: String): String? {
        if (!Identifier.isValid(text)) return null
        if (registry.has(text)) return null
        return text
    }
}