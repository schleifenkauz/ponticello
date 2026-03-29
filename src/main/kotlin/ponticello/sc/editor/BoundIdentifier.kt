package ponticello.sc.editor

import ponticello.model.ctx.Scope
import reaktive.value.ReactiveString
import reaktive.value.reactiveValue

class BoundIdentifier(override val origin: IdentifierEditor, val type: String) : Scope.BoundVariable() {
    override val name: ReactiveString get() = origin.text
    override val info: ReactiveString get() = reactiveValue(type)
}