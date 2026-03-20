package ponticello.ui.actions

import fxutils.KeyEventHandlerBody
import fxutils.actions.isTargetTextInput
import fxutils.prompt.atMouseCoords
import hextant.context.Context
import hextant.context.compoundEdit
import ponticello.impl.Logger
import ponticello.impl.unaryMinus
import ponticello.model.obj.withName
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.score.ObjectPosition
import ponticello.model.score.Score
import ponticello.model.score.ScoreObjectGroup
import ponticello.model.score.UnresolvedScoreObject
import ponticello.ui.score.*
import reaktive.value.now

object SelectionRelatedActions {
    fun addShortcuts(handler: KeyEventHandlerBody<*>, context: Context) = with(handler) {
        val selector = context[ScoreObjectSelectionManager]
        on("ESCAPE", consume = false) { ev ->
            ev.consume()
            context[ScoreObjectDuplicator].exitDuplicateMode()
            if (!ev.isAltDown) return@on
            context[ScoreObjectSelectionManager].deselectAll()
        }
        on("Ctrl+A") { ev ->
            if (ev.isTargetTextInput) return@on
            context[ScoreObjectSelectionManager].selectAll()
        }
        on("Ctrl+Shift+A") {
            val selected = resolveFocusedObject(selector) ?: return@on
            val pane = selected.parentPane
            val selection = context[ScoreObjectSelectionManager]
            selection.deselectAll()
            val instances = pane.score.instancesOf(selected.obj)
            val views = instances.map { inst -> pane.getObjectView(inst) }.toList()
            context[ScoreObjectSelectionManager].selectAll(views, addToSelection = false)
        }

        on("Shift?+G") { ev ->
            if (ev.isTargetTextInput) return@on
            val selection = RectangleSelection.get()
            if (selection != null && selection.pane is RegularScorePane) {
                val placement = ev.atMouseCoords()
                selection.pane.addNewGroup(placement, selection, recurse = ev.isShiftDown)
                RectangleSelection.clear()
            } else {
                val views = selector.selectedViews
                //important to get a single ScorePane (not a single Score)
                // because we want the instances to be from one ScorePane (or the root score)
                val parentPane = views.mapTo(mutableSetOf()) { v -> v.parentPane }.singleOrNull() ?: return@on
                val instances = views.map { v -> v.instance }
                val minT = instances.minOf { inst -> inst.start }
                val minY = instances.minOf { inst -> inst.y }
                val maxT = instances.maxOf { inst -> inst.start + inst.duration }
                val maxY = instances.maxOf { inst -> inst.y + inst.height }
                val relativePosition = ObjectPosition(-minT, -minY)
                val recurse = ev.isShiftDown
                val newScore = Score()
                val name = context[ScoreObjectRegistry].availableName("group")
                val subScore = ScoreObjectGroup(newScore).withName(name)
                subScore.setInitialSize(maxT - minT, maxY - minY)
                context.compoundEdit("Create group from objects") {
                    parentPane.score.addObject(subScore, minT, minY, autoSelect = true)
                    for (inst in instances) {
                        inst.moveInto(newScore, relativePosition, recurse)
                    }
                }
            }
        }
    }

    private fun resolveFocusedObject(selector: ScoreObjectSelectionManager): ScoreObjectView? {
        val selected = selector.focusedView.now ?: return null
        val obj = selected.obj
        if (obj is UnresolvedScoreObject) {
            Logger.warn("Object is not resolved", Logger.Category.Score)
            return null
        }
        return selected
    }
}