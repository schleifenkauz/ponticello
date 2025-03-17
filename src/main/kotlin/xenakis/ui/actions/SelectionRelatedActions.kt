package xenakis.ui.actions

import fxutils.KeyEventHandlerBody
import fxutils.actions.isTargetTextInput
import fxutils.runFXWithTimeout
import hextant.undo.compoundEdit
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.copy
import xenakis.impl.unaryMinus
import xenakis.model.Logger
import xenakis.model.registry.ScoreObjectRegistry
import xenakis.model.score.*
import xenakis.ui.launcher.XenakisMainActivity
import xenakis.ui.score.ScoreObjectSelectionManager
import xenakis.ui.score.ScoreObjectView

//TODO use action system
object SelectionRelatedActions {
    fun addShortcuts(handler: KeyEventHandlerBody<*>, activity: XenakisMainActivity) = with(handler){
        val playback = activity.playback
        val scoreView = activity.scoreView
        val context = activity.context
        on("ESCAPE") {
            scoreView.endNewObject()
            scoreView.clearClipboard()
            if (!playback.player.isPlaying.now && playback.playHead.pane is ScoreObjectView) {
                val attachedView = playback.playHead.pane as ScoreObjectView
                val absoluteTime = attachedView.absolutePosition.time + playback.playHead.currentTime
                playback.attachToMainScore()
                playback.playHead.movePlayHead(absoluteTime)
            }
            context[ScoreObjectSelectionManager].deselectAll()
            context[XenakisMainActivity].scoreView.requestFocus()
            context[XenakisMainActivity].toolSelector.select(Tool.Pointer)
        }
        on("Ctrl+A") { ev ->
            if (ev.isTargetTextInput) return@on
            context[ScoreObjectSelectionManager].selectAll()
        }
        on("Ctrl+Shift+A") {
            val selected = scoreView.selector.focusedView.now ?: return@on
            val obj = selected.instance.obj
            if (obj is ScoreObject.Unresolved) {
                Logger.warn("Object is not resolved", Logger.Category.Score)
                return@on
            }
            for (inst in selected.pane.score.instancesOf(obj)) {
                if (inst != selected.instance) {
                    val view = selected.pane.getObjectView(inst)
                    context[ScoreObjectSelectionManager].select(view, addToSelection = true)
                }
            }
        }
        on("C") { ev ->
            if (ev.isTargetTextInput) return@on
            val selected = scoreView.selector.focusedView.now ?: return@on
            val obj = selected.instance.obj
            if (obj is ScoreObject.Unresolved) {
                Logger.warn("Object is not resolved", Logger.Category.Score)
                return@on
            }
            activity.toolSelector.select(Tool.Pointer)
            scoreView.setClipboard(obj, selected)
        }
        on("Ctrl+C") { ev ->
            if (ev.isTargetTextInput) return@on
            val selected = scoreView.selector.selectedInstances.toList()
            scoreView.selector.setSystemClipboard(selected)
        }
        on("X") { ev ->
            if (ev.isTargetTextInput) return@on
            activity.toolSelector.select(Tool.Pointer)
            val view = context[ScoreObjectSelectionManager].focusedView.now ?: return@on
            val inst = view.instance
            inst.score?.removeObject(inst)
            scoreView.setClipboard(inst.obj, view)
        }
        on("U") { ev ->
            if (ev.isTargetTextInput) return@on
            context.compoundEdit("Unlink object from its original") {
                for ((obj, instances) in scoreView.selector.selectedInstances.groupBy { inst -> inst.obj }) {
                    val name = context[ScoreObjectRegistry].nameForClone(obj)
                    val clone = obj.clone(name)
                    for (oldInst in instances) {
                        val newInst = ScoreObjectInstance(clone, oldInst.position, oldInst.muted.copy())
                        oldInst.score?.addObject(newInst)
                        oldInst.score?.removeObject(oldInst)
                    }
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
}