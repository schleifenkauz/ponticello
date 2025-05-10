package ponticello.ui.controls

import fxutils.prompt.TextPrompt
import ponticello.model.obj.RenamableObject
import ponticello.sc.Identifier
import reaktive.value.now

class RenamePrompt(
    private val obj: RenamableObject,
    title: String, initialName: String = obj.name.now
) : TextPrompt<Unit>(title, initialName) {
    override fun convert(text: String): Unit? {
        if (!Identifier.isValid(text)) return null
        if (!obj.canRenameTo(text)) return null
        return obj.rename(text)
    }
}