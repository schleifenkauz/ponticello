package xenakis.ui.impl

import hextant.context.Context
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.ui.actions.Action
import xenakis.ui.actions.ActionBar

class SelectorBar<T : SelectorBar.Option<T>>(val context: Context, options: List<T>, initial: T) : ActionBar() {
    private val _selected = reactiveVariable(initial)
    val selectedOption get() = _selected
    val selected get() = _selected.now

    init {
        addActions(options.map { opt -> opt.action.withContext(this) })
    }

    fun select(option: T) {
        _selected.now = option
    }

    override fun toString(): String = "${this::class.simpleName} [ selected = $selected ]"

    interface Option<T : Option<T>> {
        val action: Action<SelectorBar<T>>
    }
}