package xenakis.ui.actions

import fxutils.actions.Action
import hextant.undo.compoundEdit
import javafx.event.Event
import reaktive.value.binding.map
import reaktive.value.now
import xenakis.ui.score.ScoreObjectView

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