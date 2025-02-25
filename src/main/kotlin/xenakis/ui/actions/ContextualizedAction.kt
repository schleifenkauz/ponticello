package xenakis.ui.actions

import javafx.event.Event
import org.kordamp.ikonli.Ikon
import reaktive.value.ReactiveBoolean
import reaktive.value.ReactiveString
import reaktive.value.ReactiveValue

interface ContextualizedAction {
    val wrapped: Action<*>

    fun execute(ev: Event?)

    val description: ReactiveString

    val isApplicable: ReactiveBoolean

    val toggleState: ReactiveBoolean?

    val icon: ReactiveValue<Ikon>
}