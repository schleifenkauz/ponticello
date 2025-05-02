package xenakis.ui.actions

import fxutils.actions.Action
import fxutils.actions.ActionBar
import fxutils.actions.ContextualizedAction
import fxutils.actions.registerActions
import fxutils.registerShortcuts
import fxutils.styleClass
import hextant.context.Context
import hextant.undo.compoundEdit
import javafx.event.Event
import javafx.scene.Scene
import reaktive.value.binding.map
import reaktive.value.now
import xenakis.model.player.ScorePlayer
import xenakis.ui.launcher.XenakisLauncher
import xenakis.ui.launcher.XenakisLauncher.Companion.currentProject
import xenakis.ui.launcher.XenakisMainActivity
import xenakis.ui.score.ScoreObjectView

inline fun Action.Collector<ObjectActionContext>.addObjectAction(
    name: String,
    body: Action.Builder<ObjectActionContext>.() -> Unit
) {
    addAction(name) {
        applicableWhen { ctx -> ctx.isApplicable(name) }
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
                val selectedViews = ctx.selectedViews.toList() //copy to avoid concurrent modification
                for (view in selectedViews) {
                    action(view, ev)
                }
            }
        }
    }
}

inline fun Action.Builder<ObjectActionContext>.applicableOn(crossinline predicate: (ScoreObjectView) -> Boolean) {
    applicableWhen { ctx -> ctx.focusedView.map { v -> v != null && predicate(v) } }
}

inline fun <reified V> Action.Builder<ObjectActionContext>.applicableOn() {
    applicableOn { v -> v is V }
}

fun Action.Builder<ObjectActionContext>.executeSingle(action: (ScoreObjectView, Event?) -> Unit) {
    executes { ctx, ev -> ctx.focusedView.now?.let { action(it, ev) } }
}

fun Scene.registerGlobalShortcuts(context: Context) {
    registerShortcuts {
        registerActions(PlaybackActions.withContext(context[ScorePlayer.CURRENT]))
        registerActions(ToolWindowActions.withContext(context[XenakisMainActivity]))
        registerActions(ServerActions.withContext(context[currentProject]))
        registerActions(ProjectActions.withContext(context[XenakisLauncher]))
    }
}

fun toolbarPart(actions: List<ContextualizedAction>) =
    ActionBar(actions, buttonStyle = "large-icon-button").styleClass("toolbar-part")
