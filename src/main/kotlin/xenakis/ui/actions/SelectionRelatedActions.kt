package xenakis.ui.actions

import fxutils.KeyEventHandlerBody
import fxutils.actions.isTargetTextInput
import fxutils.runFXWithTimeout
import hextant.undo.compoundEdit
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.Logger
import xenakis.impl.unaryMinus
import xenakis.model.registry.ScoreObjectRegistry
import xenakis.model.score.*
import xenakis.ui.launcher.XenakisMainActivity
import xenakis.ui.score.ScoreObjectSelectionManager
import xenakis.ui.score.ScoreObjectView

object SelectionRelatedActions {
    fun addShortcuts(handler: KeyEventHandlerBody<*>, activity: XenakisMainActivity) = with(handler){
        val playback = activity.playback
        val scoreView = activity.scoreView
        val context = activity.context
        on("ESCAPE") {
            scoreView.clearRegionSelection()
            scoreView.clearClipboard()
            if (!playback.player.isPlaying.now && playback.playHead.pane is ScoreObjectView) {
                val attachedView = playback.playHead.pane as ScoreObjectView
                val absoluteTime = attachedView.absolutePosition.time + playback.playHead.currentTime
                playback.attachToMainScore()
                playback.playHead.movePlayHead(absoluteTime)
            }
            context[ScoreObjectSelectionManager].deselectAll()
            context[XenakisMainActivity].scoreView.requestFocus()
        }
        on("Ctrl+A") { ev ->
            if (ev.isTargetTextInput) return@on
            context[ScoreObjectSelectionManager].selectAll()
        }
        on("Ctrl+Shift+A") {
            val selected = resolveFocusedObject(scoreView.selector) ?: return@on
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
            val views = scoreView.selector.selectedViews
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
            parentPane.score.addObject(newInst)
            context.compoundEdit("Create group from objects") {
                for (inst in instances) {
                    inst.moveInto(newScore, relativePosition, recurse)
                }
            }
            runFXWithTimeout {
                val view = parentPane.getObjectView(newInst)
                context[ScoreObjectSelectionManager].select(view, addToSelection = false)
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