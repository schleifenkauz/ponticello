package ponticello.model.ctx

import ponticello.sc.editor.IdentifierEditor
import reaktive.value.ReactiveString
import reaktive.value.reactiveValue

class BoundIdentifier(override val origin: IdentifierEditor, val type: String) : BoundVariable() {
    override val name: ReactiveString get() = origin.text

    override val info: ReactiveString get() = reactiveValue(type)
}