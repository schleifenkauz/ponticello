package ponticello.ui.controls

import fxutils.controls.EditableText
import ponticello.model.obj.RenamableObject
import ponticello.sc.Identifier

class NameControl(val obj: RenamableObject) : EditableText(obj.name) {
    override fun updateText(value: String) {
        obj.rename(value)
    }

    override fun isValid(text: String): Boolean = Identifier.isValid(text) && obj.canRenameTo(text)
}