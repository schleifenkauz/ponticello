package ponticello.ui.dock

import fxutils.Shortcut
import fxutils.actions.Action
import fxutils.actions.ContextualizedAction
import fxutils.shortcut
import javafx.event.Event
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import org.kordamp.ikonli.Ikon
import reaktive.value.ReactiveBoolean
import reaktive.value.ReactiveString
import reaktive.value.ReactiveValue
import reaktive.value.reactiveValue

class ToolPaneAction(private val toolPane: ToolPane) : ContextualizedAction {
    override val shortcuts: List<Shortcut>
        get() {
            val str = toolPane.shortcut
            return when {
                str == null -> emptyList()
                str.startsWith("F") -> listOf("Ctrl?+$str".shortcut)
                else -> listOf(str.shortcut)
            }
        }
    override val description: ReactiveString
        get() = reactiveValue(toolPane.type!!.title)
    override val icon: ReactiveValue<Ikon?>
        get() = reactiveValue(toolPane.type!!.icon)
    override val ifNotApplicable: Action.IfNotApplicable
        get() = Action.IfNotApplicable.Hide
    override val isApplicable: ReactiveBoolean
        get() = reactiveValue(true)
    override val toggleState: ReactiveBoolean
        get() = toolPane.isShowing

    override fun execute(ev: Event?) {
        when {
            ev is MouseEvent && ev.button == MouseButton.PRIMARY ->
                toolPane.toggleShowing(toggleExclusive = ev.isControlDown)
            ev is MouseEvent && ev.button == MouseButton.SECONDARY -> toolPane.showToolPaneConfigMenu(ev)
            ev is KeyEvent -> toolPane.handleShortcut(ev)
        }
    }
}