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
import reaktive.value.binding.and
import reaktive.value.binding.map
import reaktive.value.binding.not
import reaktive.value.binding.notEqualTo
import reaktive.value.forEach
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import xenakis.ui.impl.SelectorBar
import xenakis.ui.impl.neverHGrow
import xenakis.ui.impl.setPseudoClassState
import xenakis.ui.impl.styleClass
import xenakis.ui.score.ScoreObjectView

fun <C> collectActions(body: Action.Collector<C>.() -> Unit): Action.Collector<C> = Action.Collector<C>().apply(body)

fun KeyEventHandlerBody<*>.registerActions(actions: List<ContextualizedAction>) {
    for (action in actions) {
        for (shortcut in action.wrapped.shortcuts) {
            on(shortcut) { ev ->
                if (action.isApplicable.now) {
                    action.execute(ev)
                }
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
    executes { ctx, ev ->
        val selected = ctx.selectedViews
        when {
            selected.isEmpty() -> return@executes
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
    executes { ctx, ev -> ctx.focusedView.now?.let { action(it, ev) } }
}

val Event?.isTargetTextInput get() = this is KeyEvent && (target is TextInputControl || target is Spinner<*>)

const val DEFAULT_RADIUS: Double = 16.0

fun ContextualizedAction.makeButton(style: String = "tool-button"): ButtonBase {
    val button = Button()
    val iconObserver = this.icon.forEach { icon ->
        Platform.runLater {
            button.graphic = FontIcon(icon)
        }
    }
    val toggleState = this.toggleState
    button.userData = if (toggleState != null) {
        val toggleStateObserver = toggleState.forEach { active ->
            Platform.runLater { button.setPseudoClassState("selected", active) }
        }
        iconObserver and toggleStateObserver
    } else iconObserver
    val iconAvailable = this.icon.notEqualTo(Action.NO_ICON)
    val applicable = this.isApplicable
    if (this.wrapped.ifNotApplicable == Action.IfNotApplicable.Disable) {
        button.visibleProperty().bind(iconAvailable.asObservableValue())
        button.disableProperty().bind(applicable.not().asObservableValue())
    } else {
        button.visibleProperty().bind(iconAvailable.and(applicable).asObservableValue())
    }
    button.tooltip = Tooltip().also { tooltip ->
        val shortcutInfo = this.wrapped.shortcuts
            .firstOrNull()
            ?.let { shortcut -> " ($shortcut)" }
            .orEmpty()
        tooltip.userData = this.description.forEach { desc ->
            Platform.runLater {
                tooltip.text = "$desc $shortcutInfo"
            }
        }
    }
    button.setMinSize(DEFAULT_RADIUS * 2, DEFAULT_RADIUS * 2)
    button.setMaxSize(DEFAULT_RADIUS * 2, DEFAULT_RADIUS * 2)
    button.neverHGrow()
    button.styleClass.addAll(style, "icon-button")
    button.setOnMouseClicked { ev -> this.execute(ev) }
    return button
}

fun <C> Action.Collector<C>.actionBar(context: C) = ActionBar(withContext(context))

private fun ButtonBase.makeIconButton(ikon: Ikon, description: String) {
    styleClass("icon-button")
    graphic = FontIcon(ikon)
    tooltip = Tooltip(description)
    neverHGrow()
}

@Deprecated(message = "User action system instead")
fun Ikon.button(action: String, execute: () -> Unit = {}): Button {
    val button = Button()
    button.makeIconButton(this, action)
    button.setOnMouseClicked { execute() }
    return button
}

fun <C> action(name: String, config: Action.Builder<C>.() -> Unit) = Action.Builder<C>(name).apply(config).build()

fun <T : SelectorBar.Option<T>> Action.Builder<SelectorBar<T>>.selects(value: T) {
    selects(value) { bar -> bar.selectedOption }
}