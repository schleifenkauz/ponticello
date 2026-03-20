package ponticello.ui.score

import fxutils.Alt
import fxutils.Shift
import fxutils.modifiers
import fxutils.prompt.PromptPlacement
import fxutils.prompt.nextToTarget
import hextant.context.Context
import hextant.context.compoundEdit
import javafx.scene.input.MouseEvent
import ponticello.model.obj.withName
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.score.Score
import ponticello.model.score.ScoreObject
import ponticello.model.score.ScoreObjectGroup
import ponticello.model.score.ScoreObjectInstance
import ponticello.model.server.BufferRegistry
import ponticello.ui.registry.SimpleRegistrySelectorPrompt

abstract class RegularScorePane(score: Score, context: Context) : ScorePane(score, context) {
    override fun acceptObject(obj: ScoreObject): ScoreObject? = obj

    override fun rightClicked(ev: MouseEvent) {
        when (ev.modifiers) {
            setOf(Alt) -> {
                val popup = SimpleRegistrySelectorPrompt(context[ScoreObjectRegistry], "Add object instance")
                val anchor = localToScreen(ev.x, ev.y)
                val obj = popup.showPopup(anchor, scene.window) ?: return
                val pos = snapToGrid(ev.x, ev.y)
                val inst = ScoreObjectInstance(obj, pos)
                score.addObject(inst, autoSelect = true)
            }

            setOf(Shift) -> {
                val popup = SimpleRegistrySelectorPrompt(context[BufferRegistry], "Place sample")
                val anchor = localToScreen(ev.x, ev.y)
                val sample = popup.showPopup(anchor, scene.window) ?: return
                val pos = snapToGrid(ev.x, ev.y)
                ScorePaneDropHandler.createPlayBufObject(sample, pos, ev.nextToTarget(), this)
            }

            else -> super.rightClicked(ev)
        }
    }

    fun addNewGroup(promptPlacement: PromptPlacement, selection: RectangleSelection, recurse: Boolean) {
        val containedViews = selection.containedViews(mustBeContainedEntirely = true)
        val name = context[ScoreObjectRegistry].nameForGroup(promptPlacement) ?: return
        context.compoundEdit("Add object group") {
            val subScore = Score(mutableListOf())
            val groupObj = ScoreObjectGroup(subScore).withName(name)
            val inst = addObject(groupObj, selection)
            val relativePosition = -inst.position
            for (view in containedViews) {
                view.instance.moveInto(subScore, relativePosition, recurse)
            }
        }
    }

    fun addObject(obj: ScoreObject, rect: RectangleSelection): ScoreObjectInstance {
        val registry = context[ScoreObjectRegistry]
        if (obj in registry) registry.add(obj)
        val inst = rect.createInstance(obj)
        score.addObject(inst, autoSelect = true)
        return inst
    }
}