package ponticello.ui.actions

import fxutils.actions.Action
import fxutils.actions.isAltDown
import fxutils.actions.isShiftDown
import fxutils.prompt.SimpleSearchableListView
import hextant.core.editor.defaultState
import hextant.serial.EditorRoot
import javafx.geometry.Point2D
import javafx.scene.robot.Robot
import ponticello.model.obj.*
import ponticello.model.project.METERS
import ponticello.model.project.get
import ponticello.model.registry.InstrumentRegistry
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.registry.reference
import ponticello.model.score.*
import ponticello.sc.editor.CodeBlockEditor
import ponticello.ui.actions.ScoreActions.createSoundObject
import ponticello.ui.actions.ScoreActions.getScorePaneAtCursor
import ponticello.ui.controls.DecimalPrompt
import ponticello.ui.controls.NamePrompt
import ponticello.ui.registry.SimpleSearchableRegistryView
import ponticello.ui.score.*
import reaktive.value.now
import reaktive.value.reactiveVariable
import kotlin.reflect.KClass

object ScoreActions : Action.Collector<NavigableScorePane>({
    addAction("Add tempo grid") {
        shortcut("L")
        executes { pane ->
            if (RectangleSelection.get() != null) return@executes
            val (pane, pos) = pane.getScorePaneAtCursor() ?: return@executes
            val options = listOf(null) + pane.context.project[METERS]
            //TODO
        }
    }
    addAction("Add task") {
        shortcut("T")
        executes { pane ->
            if (RectangleSelection.get() != null) return@executes
            val (pane, pos) = pane.getScorePaneAtCursor() ?: return@executes
            if (pane is RegularScorePane) {
                val defaultName = pane.context[ScoreObjectRegistry].availableName("task")
                val name = NamePrompt(pane.context[ScoreObjectRegistry], "Task name", defaultName)
                    .showDialog(pane.scene.window, Robot().mousePosition) ?: return@executes
                val code = EditorRoot(CodeBlockEditor().defaultState())
                val obj = TaskObject(code).withName(name)
                val inst = ScoreObjectInstance(obj, pos)
                pane.score.addObject(inst, autoSelect = true)
            }
        }
    }
    addAction("Add time") {
        shortcut("Ctrl+INSERT")
        executes { pane ->
            if (RectangleSelection.get() != null) return@executes
            val (pane, pos) = pane.getScorePaneAtCursor() ?: return@executes
            val amount = DecimalPrompt(
                "How much time to add",
                precision = 2, initialValue = 10.0, 0.0..1000.0
            ).showDialog() ?: return@executes
            pane.score.addTime(pos.time, amount)
        }
    }
    addAction("Select objects in region") {
        shortcut("Shift?+Alt?+Enter")
        executes { _, ev ->
            val selection = RectangleSelection.get() ?: return@executes
            RectangleSelection.clear()
            val containedViews = selection
                .pane.viewsInside(selection.bounds, mustBeContainedEntirely = ev.isAltDown())
            val selector = selection.pane.context[ScoreObjectSelectionManager]
            selector.selectAll(containedViews, addToSelection = ev.isShiftDown())
            selection.pane.requestFocus()
        }
    }
    addAction("Delete time range") {
        shortcut("Ctrl+DELETE")
        executes { _, _ ->
            val selection = RectangleSelection.get() ?: return@executes
            RectangleSelection.clear()
            selection.pane.score.deleteTimeRange(selection.time, selection.time + selection.duration)
        }
    }
    addAction("Create sound object") {
        shortcut("S")
        executes { _ ->
            val selection = RectangleSelection.get() ?: return@executes
            val pane = selection.pane as? RegularScorePane ?: return@executes
            val synthObj = pane.createSoundObject(anchor = Robot().mousePosition) ?: return@executes
            RectangleSelection.clear()
            pane.addObject(synthObj, selection)
        }
    }
    addAction("Create MIDI object") {
        shortcut("K")
        executes { _ ->
            val selection = RectangleSelection.get() ?: return@executes
            val pane = selection.pane as? RegularScorePane ?: return@executes
            val context = pane.context
            val anchor = Robot().mousePosition
            val midiInstrument = MidiInstrumentSelectorPopup(context)
                .showPopup(anchor, pane.scene.window) ?: return@executes
            val midiObj = MidiObjectView.createNewMidiObjectDialog(midiInstrument, context)
                .showDialog(pane.scene.window, anchor) ?: return@executes
            RectangleSelection.clear()
            pane.addObject(midiObj, selection)
        }
    }
}) {
    private fun NavigableScorePane.getScorePaneAtCursor(): Pair<ScorePane, ObjectPosition>? {
        val coords = screenToLocal(Robot().mousePosition)
        if (!boundsInLocal.contains(coords)) return null
        val pos = snapToGrid(coords.x, coords.y)
        return getScorePaneContaining(pos)
    }

    private fun ScorePane.getScorePaneContaining(pos: ObjectPosition): Pair<ScorePane, ObjectPosition> {
        for (view in allViews) {
            if (view !is AbstractScoreObjectGroupView) continue
            val inst = view.instance
            if (pos.time in inst.timeRange && pos.y in inst.yRange) {
                return view.scorePane.getScorePaneContaining(pos - inst.position)
            }

        }
        return Pair(this, pos)
    }

    private fun RegularScorePane.createSoundObject(anchor: Point2D): SoundProcess? {
        val options = context[InstrumentRegistry]
        val instrument = SimpleSearchableRegistryView(options, "Select instrument")
            .showPopup(anchor, scene.window) ?: return null
        val defaultBus = (associatedObject as? ScoreObjectGroup)?.defaultBusRef?.now?.get()
        val controls = SoundProcessView.getInitialControls(
            instrument, context, defaultBus, anchor
        ) ?: return null
        val initialName = context[ScoreObjectRegistry].availableName(prefix = instrument.name.now)
        val name = NamePrompt(context[ScoreObjectRegistry], "Name for object", initialName)
            .showDialog(scene.window, anchor) ?: return null
        return SoundProcess(reactiveVariable(instrument.reference()), controls).withName(name)
    }
}