package xenakis.sc.view

import hextant.core.EditorView
import xenakis.model.NamedObject

interface ObjectSelectorView<in O : NamedObject> : EditorView {
    fun selected(obj: O)
}