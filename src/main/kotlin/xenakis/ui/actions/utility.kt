package xenakis.ui.actions

import hextant.fx.KeyEventHandlerBody
import hextant.fx.registerShortcuts
import hextant.undo.compoundEdit
import javafx.application.Platform
import javafx.event.Event
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.javafx.FontIcon
import reaktive.value.*
import reaktive.value.binding.and
import reaktive.value.binding.map
import reaktive.value.binding.not
import reaktive.value.binding.notEqualTo
import reaktive.value.fx.asObservableValue
import xenakis.ui.impl.neverHGrow
import xenakis.ui.impl.styleClass
import xenakis.ui.score.ScoreObjectView

fun <C> collectActions(body: Action.Collector<C>.() -> Unit): Action.Collector<C> = Action.Collector<C>().apply(body)

fun KeyEventHandlerBody<*>.registerActions(actions: List<ContextualizedAction>) {
    for (action in actions) {
        on(action.wrapped.shortcut) { ev ->
            if (action.isApplicable().now) {
                action.execute(ev)
            }
        }
    }
}

fun Node.registerShortcuts(actions: List<ContextualizedAction>) {
    registerShortcuts {
        registerActions(actions)
    }
}

fun Event?.isShiftDown() = (this is KeyEvent && isShiftDown) || (this is MouseEvent && isShiftDown)

fun Event?.isAltDown() = (this is KeyEvent && isAltDown) || (this is MouseEvent && isAltDown)

fun Event?.isControlDown() = (this is KeyEvent && isControlDown) || (this is MouseEvent && isControlDown)

inline fun Action.Collector<ObjectActionContext>.addObjectAction(
    name: String,
    body: Action.Builder<ObjectActionContext>.() -> Unit
) {
    addAction(name) {
        applicableIf { ctx -> ctx.isApplicable(name) }
        body()
    }
}

fun Action.Builder<ObjectActionContext>.executeMultiAction(action: (ScoreObjectView, Event?) -> Unit) {
    execute { ctx, ev ->
        val selected = ctx.selectedViews
        when {
            selected.isEmpty() -> return@execute
            selected.size == 1 -> action(selected.single(), ev)
            else -> ctx.context.compoundEdit(name) {
                for (view in ctx.selectedViews) {
                    action(view, ev)
                }
            }
        }
    }
}

inline fun Action.Builder<ObjectActionContext>.applicableOn(crossinline predicate: (ScoreObjectView) -> Boolean) {
    applicableIf { ctx -> ctx.focusedView.map { v -> v != null && predicate(v) } }
}

inline fun <reified V> Action.Builder<ObjectActionContext>.applicableOn() {
    applicableOn { v -> v is V }
}

fun Action.Builder<ObjectActionContext>.executeSingle(action: (ScoreObjectView, Event?) -> Unit) {
    execute { ctx, ev -> ctx.focusedView.now?.let { action(it, ev) } }
}

val Event?.isTargetTextInput get() = this is KeyEvent && (target is TextInputControl || target is Spinner<*>)

const val DEFAULT_RADIUS: Double = 16.0

fun configureButton(button: ButtonBase, action: ContextualizedAction) {
    button.userData = action.icon.forEach { icon ->
        Platform.runLater {
            button.graphic = FontIcon(icon)
        }
    }
    val iconAvailable = action.icon.notEqualTo(Action.NO_ICON)
    val applicable = action.isApplicable()
    if (action.wrapped.ifNotApplicable == Action.IfNotApplicable.Disable) {
        button.visibleProperty().bind(iconAvailable.asObservableValue())
        button.disableProperty().bind(applicable.not().asObservableValue())
    } else {
        button.visibleProperty().bind(iconAvailable.and(applicable).asObservableValue())
    }
    button.tooltip = Tooltip().also { tooltip ->
        tooltip.userData = action.getDescription().forEach { desc ->
            Platform.runLater {
                tooltip.text = "$desc (${action.wrapped.shortcut})"
            }
        }
    }
    button.setMinSize(DEFAULT_RADIUS * 2, DEFAULT_RADIUS * 2)
    button.setMaxSize(DEFAULT_RADIUS * 2, DEFAULT_RADIUS * 2)
    button.neverHGrow()
    button.styleClass("icon-button")
    button.setOnMouseClicked { ev -> action.execute(ev) }
}

fun ContextualizedAction.makeToggleButton(variable: ReactiveValue<Boolean>): ToggleButton {
    val button = ToggleButton()
    configureButton(button, this)
    button.userData = variable.observe { _, v -> button.isSelected = v }
    return button
}

fun <C> Action.Builder<C>.toggles(variable: (C) -> ReactiveVariable<Boolean>) {
    execute { ctx ->
        val v = variable(ctx)
        v.now = !v.now
    }
}

private fun ButtonBase.makeIconButton(ikon: Ikon, description: String) {
    styleClass("icon-button")
    graphic = FontIcon(ikon)
    tooltip = Tooltip(description)
    setMinSize(DEFAULT_RADIUS * 2, DEFAULT_RADIUS * 2)
    setMaxSize(DEFAULT_RADIUS * 2, DEFAULT_RADIUS * 2)
    neverHGrow()
}

@Deprecated(message = "User action system instead")
fun Ikon.button(action: String, execute: () -> Unit = {}): Button {
    val button = Button()
    button.makeIconButton(this, action)
    button.setOnMouseClicked { execute() }
    return button
}

fun Ikon.toggleButton(description: String): ToggleButton {
    val button = ToggleButton()
    button.makeIconButton(this, description)
    return button
}