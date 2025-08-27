package ponticello.ui.score

import fxutils.*
import fxutils.actions.isShiftDown
import fxutils.prompt.SimpleSearchableListView
import hextant.context.Context
import hextant.context.compoundEdit
import hextant.core.editor.defaultState
import hextant.serial.EditorRoot
import javafx.event.Event
import javafx.geometry.Point2D
import javafx.scene.input.MouseEvent
import javafx.scene.robot.Robot
import ponticello.model.obj.*
import ponticello.model.registry.*
import ponticello.model.score.*
import ponticello.sc.editor.CodeBlockEditor
import ponticello.ui.controls.NamePrompt
import ponticello.ui.registry.SimpleSearchableRegistryView
import reaktive.value.now
import reaktive.value.reactiveVariable
import kotlin.reflect.KClass

abstract class RegularScorePane(score: Score, context: Context) : ScorePane(score, context) {
    override fun acceptObject(obj: ScoreObject): ScoreObject? = obj.takeIf { it !is MidiNoteObject }

    override fun rightClicked(ev: MouseEvent) {
        when (ev.modifiers) {
            setOf(Alt) -> {
                val popup = SimpleSearchableRegistryView(context[ScoreObjectRegistry], "Add object instance")
                val anchor = localToScreen(ev.x, ev.y)
                val obj = popup.showPopup(anchor, scene.window) ?: return
                val pos = snapToGrid(ev.x, ev.y)
                val inst = ScoreObjectInstance(obj, pos)
                score.addObject(inst, autoSelect = true)
            }

            setOf(Shift) -> {
                val popup = SimpleSearchableRegistryView(context[BufferRegistry], "Place sample")
                val anchor = localToScreen(ev.x, ev.y)
                val sample = popup.showPopup(anchor, scene.window) ?: return
                val pos = snapToGrid(ev.x, ev.y)
                ScorePaneDropHandler.createPlayBufObject(sample, pos, ev, this)
            }

            else -> super.rightClicked(ev)
        }
    }

    override fun doubleClicked(ev: MouseEvent) {
        ev.consume()
        val defaultName = context[ScoreObjectRegistry].availableName("memo")
        val obj = MemoObject("").withName(defaultName)
        val (t, y) = snapToGrid(ev.x, ev.y)
        val inst = ScoreObjectInstance(obj, t, y)
        score.addObject(inst, autoSelect = true)
        val view = getObjectView(inst) as MemoObjectView
        runFXWithTimeout(20) {
            view.enterEdit()
        }
    }

    fun addNewGroup(ev: Event?, selection: RectangleSelection) {
        val containedViews = viewsInside(selection.bounds, mustBeContainedEntirely = true)
        val name = context[ScoreObjectRegistry].nameForGroup(ev) ?: return
        context.compoundEdit("Add object group") {
            val subScore = Score(mutableListOf())
            val groupObj = ScoreObjectGroup(subScore).withName(name)
            val inst = addObject(groupObj, selection)
            val relativePosition = -inst.position
            for (view in containedViews) {
                view.instance.moveInto(subScore, relativePosition, recurse = ev.isShiftDown())
            }
        }
    }

    fun addObject(obj: ScoreObject, rect: RectangleSelection): ScoreObjectInstance {
        val registry = context[ScoreObjectRegistry]
        if (obj in registry) registry.add(obj)
        val inst = rect.createInstance(obj)
        obj.liveConfig.yPosition.set(this.absolutePosition.y + inst.y)
        score.addObject(inst, autoSelect = true)
        return inst
    }
}