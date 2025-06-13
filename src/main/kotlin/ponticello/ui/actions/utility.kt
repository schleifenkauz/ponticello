package ponticello.ui.actions

import fxutils.actions.Action
import fxutils.actions.ActionBar
import fxutils.actions.ContextualizedAction
import fxutils.actions.registerActions
import fxutils.registerShortcuts
import fxutils.styleClass
import fxutils.undo.UndoManager
import hextant.context.Context
import hextant.context.compoundEdit
import javafx.event.Event
import javafx.scene.Scene
import ponticello.model.obj.ContextualObject
import ponticello.model.obj.project
import ponticello.model.player.ScorePlayer
import ponticello.ui.launcher.PonticelloLauncher
import ponticello.ui.score.ScoreObjectView
import reaktive.value.binding.map
import reaktive.value.now

inline fun Action.Collector<ObjectActionContext>.addObjectAction(
    name: String,
    body: Action.Builder<ObjectActionContext>.() -> Unit
) {
    addAction(name) {
        enableWhen { ctx -> ctx.isApplicable(name) }
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
    enableWhen { ctx -> ctx.focusedView.map { v -> v != null && predicate(v) } }
    ifNotApplicable(Action.IfNotApplicable.Hide)
}

inline fun <reified V> Action.Builder<ObjectActionContext>.applicableOn() {
    applicableOn { v -> v is V }
}

fun Action.Builder<ObjectActionContext>.executeSingle(action: (ScoreObjectView, Event?) -> Unit) {
    executes { ctx, ev -> ctx.focusedView.now?.let { action(it, ev) } }
}

fun Scene.registerGlobalShortcuts(context: Context) {
    registerShortcuts {
        registerActions(PlaybackActions.global.withContext(context[ScorePlayer.CURRENT]))
        registerActions(ServerActions.withContext(context.project))
        registerActions(ProjectActions.withContext(context[PonticelloLauncher]))
    }
}

fun toolbarPart(actions: List<ContextualizedAction>): ActionBar {
    val bar = ActionBar(actions, buttonStyle = "large-icon-button")
    bar.styleClass("tool-bar")
    return bar
}

fun Action.Builder<out ContextualObject>.undoable() {
    undoable { obj -> obj.context[UndoManager] }
}