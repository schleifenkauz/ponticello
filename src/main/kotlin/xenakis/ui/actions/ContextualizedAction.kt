package xenakis.ui.actions

import javafx.event.Event
import org.kordamp.ikonli.Ikon
import reaktive.value.ReactiveBoolean
import reaktive.value.ReactiveString
import reaktive.value.ReactiveValue

interface ContextualizedAction {
    val wrapped: Action<*>

    fun execute(ev: Event?)

    fun getDescription(): ReactiveString

    fun isApplicable(): ReactiveBoolean

    fun toggleState(): ReactiveBoolean?

    val icon: ReactiveValue<Ikon>
}