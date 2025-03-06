package xenakis.ui.controls

import fxutils.prompt.TextPrompt
import xenakis.model.registry.ObjectRegistry
import xenakis.sc.Identifier

class NamePrompt(
    private val registry: ObjectRegistry<*>, title: String, initialName: String
) : TextPrompt<String>(title, initialName) {
    override fun convert(text: String): String? {
        if (!Identifier.isValid(text)) return null
        if (registry.has(text)) return null
        return text
    }
}