package xenakis.ui.actions

import fxutils.KeyEventHandlerBody
import fxutils.actions.isTargetTextInput
import hextant.context.Context
import hextant.undo.compoundEdit
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.Logger
import xenakis.impl.unaryMinus
import xenakis.model.player.ScorePlayer
import xenakis.model.registry.ScoreObjectRegistry
import xenakis.model.score.*
import xenakis.ui.score.ScoreObjectDuplicator
import xenakis.ui.score.ScoreObjectSelectionManager
import xenakis.ui.score.ScoreObjectView
import xenakis.ui.score.ScorePane

object SelectionRelatedActions {
    fun addShortcuts(handler: KeyEventHandlerBody<*>, context: Context) = with(handler){
        val player = context[ScorePlayer.CURRENT]
        val selector = context[ScoreObjectSelectionManager]
        on("ESCAPE") {
            context[ScoreObjectDuplicator].exitDuplicateMode()
            if (!player.isPlaying.now && player.playHead.pane is ScoreObjectView) {
                val attachedView = player.playHead.pane as ScoreObjectView
                val absoluteTime = attachedView.absolutePosition.time + player.playHead.currentTime
                player.attachToScoreView(context[ScorePane.CURRENT_ROOT])
                player.playHead.movePlayHead(absoluteTime)
            }
            context[ScoreObjectSelectionManager].deselectAll()
        }
        on("Ctrl+A") { ev ->
            if (ev.isTargetTextInput) return@on
            context[ScoreObjectSelectionManager].selectAll()
        }
        on("Ctrl+Shift+A") {
            val selected = resolveFocusedObject(selector) ?: return@on
            val pane = selected.pane
            for (inst in pane.score.instancesOf(selected.instance.obj)) {
                if (inst != selected.instance) {
                    val view = pane.getObjectView(inst)
                    context[ScoreObjectSelectionManager].select(view, addToSelection = true)
                }
            }
        }

        on("Shift?+G") { ev ->
            if (ev.isTargetTextInput) return@on
            val views = selector.selectedViews
            //import to get a single ScorePane (not a single Score)
            // because we want the instances to be from one ScorePane (or the root score)
            val parentPane = views.mapTo(mutableSetOf()) { v -> v.pane }.singleOrNull() ?: return@on
            val instances = views.map { v -> v.instance }
            val minT = instances.minOf { inst -> inst.start }
            val minY = instances.minOf { inst -> inst.y }
            val maxT = instances.maxOf { inst -> inst.start + inst.duration }
            val maxY = instances.maxOf { inst -> inst.y + inst.height }
            val relativePosition = ObjectPosition(-minT, -minY)
            val recurse = ev.isShiftDown
            val newScore = Score()
            val name = context[ScoreObjectRegistry].availableName("group")
            val newObj = ScoreObjectGroup(reactiveVariable(name), newScore)
            newObj.setInitialSize(maxT - minT, maxY - minY)
            val newInst = ScoreObjectInstance(newObj, minT, minY)
            parentPane.score.addObject(newInst, autoSelect = true)
            context.compoundEdit("Create group from objects") {
                for (inst in instances) {
                    inst.moveInto(newScore, relativePosition, recurse)
                }
            }
        }
    }

    private fun resolveFocusedObject(selector: ScoreObjectSelectionManager): ScoreObjectView? {
        val selected = selector.focusedView.now ?: return null
        val obj = selected.instance.obj
        if (obj is ScoreObject.Unresolved) {
            Logger.warn("Object is not resolved", Logger.Category.Score)
            return null
        }
        return selected
    }
}