package ponticello.ui.actions

import fxutils.actions.Action
import fxutils.actions.isAltDown
import fxutils.actions.isShiftDown
import fxutils.actions.isTargetTextInput
import fxutils.prompt.IntegerPrompt
import hextant.context.compoundEdit
import hextant.core.editor.defaultState
import hextant.serial.EditorRoot
import javafx.geometry.Point2D
import javafx.scene.robot.Robot
import ponticello.impl.times
import ponticello.impl.zero
import ponticello.model.obj.withName
import ponticello.model.player.MeterRegistry
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.registry.reference
import ponticello.model.score.*
import ponticello.sc.editor.CodeBlockEditor
import ponticello.ui.actions.ScoreActions.createSoundObject
import ponticello.ui.actions.ScoreActions.getScorePaneAtCursor
import ponticello.ui.controls.DecimalPrompt
import ponticello.ui.controls.NamePrompt
import ponticello.ui.registry.MeterSelectorPrompt
import ponticello.ui.score.*
import reaktive.value.now
import reaktive.value.reactiveVariable

object ScoreActions : Action.Collector<NavigableScorePane>({
    addAction("Add tempo grid") {
        shortcut("Alt?+T")
        executes { rootPane, ev ->
            if (ev.isTargetTextInput && !ev.isShiftDown()) return@executes
            if (RectangleSelection.get() != null) return@executes
            if (!rootPane.isFocusWithin) return@executes
            val (pane, _) = rootPane.getScorePaneAtCursor() ?: return@executes
            val anchor = Robot().mousePosition
            rootPane.context.compoundEdit("Add tempo grid") {
                //If I set owner=rootPane.scene.window, it crashes the JVM somehow...
                val meter = MeterSelectorPrompt(pane.context[MeterRegistry], "Select meter")
                    .showDialog(owner = null, anchor) ?: return@executes
                val bars = IntegerPrompt("Number of bars", 1, 1..Int.MAX_VALUE)
                    .showDialog(owner = null, anchor) ?: return@executes
                val name = pane.context[ScoreObjectRegistry].availableName(meter.name.now)
                val obj = TempoGridObject(meter.reference())
                obj.setInitialName(name)
                val duration = meter.getDuration(TimeUnit.Bars) * bars
                obj.setInitialSize(duration, height = zero)
                val point = pane.screenToLocal(anchor)
                val pos = rootPane.snapToGrid(point.x, point.y)
                val inst = ScoreObjectInstance(obj, pos)
                pane.score.addObject(inst, autoSelect = true)
            }
        }
    }
    addAction("Add task") {
        shortcut("Ctrl+T") //TODO maybe this can be done by dropping a ScriptObject
        executes { rootPane ->
            if (RectangleSelection.get() != null) return@executes
            val (pane, pos) = rootPane.getScorePaneAtCursor() ?: return@executes
            if (pane is AbstractScorePane) {
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
        executes { rootPane ->
            if (RectangleSelection.get() != null) return@executes
            val (pane, pos) = rootPane.getScorePaneAtCursor() ?: return@executes
            val amount = DecimalPrompt(
                "How much time to add",
                precision = 2, initialValue = 10.0, 0.0..1000.0
            ).showDialog() ?: return@executes
            pane.score.addTime(pos.time, amount)
        }
    }
    addAction("Select objects in region") {
        shortcut("Shift?+Alt?+S")
        executes { _, ev ->
            val selection = RectangleSelection.get() ?: return@executes
            val containedViews = selection.containedViews(mustBeContainedEntirely = ev.isAltDown())
            val selector = selection.pane.context[ScoreObjectSelectionManager]
            selector.selectAll(containedViews, addToSelection = ev.isShiftDown())
            selection.pane.requestFocus()
            RectangleSelection.clear()
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
        shortcut("Enter")
        executes { _ ->
            val selection = RectangleSelection.get() ?: return@executes
            val pane = selection.pane as? AbstractScorePane ?: return@executes
            val synthObj = pane.createSoundObject(anchor = Robot().mousePosition) ?: return@executes
            RectangleSelection.clear()
            pane.addObject(synthObj, selection)
        }
    }
    addAction("Create MIDI object") {
        shortcut("K")
        executes { _ ->
            val selection = RectangleSelection.get() ?: return@executes
            val pane = selection.pane as? AbstractScorePane ?: return@executes
            val context = pane.context
            val anchor = Robot().mousePosition
            val midiInstrument = InstrumentSelectorPopup(context)
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

    private fun AbstractScorePane.createSoundObject(anchor: Point2D): SoundProcess? {
        val instrument = InstrumentSelectorPopup(context).showPopup(anchor, scene.window) ?: return null
        val defaultBus = (associatedObject as? ScoreObjectGroup)?.defaultBusRef?.now?.get()
        val controls = SoundProcessView.getInitialControls(
            instrument.get()!!, context, defaultBus, anchor
        ) ?: return null
        val initialName = context[ScoreObjectRegistry].availableName(prefix = instrument.getName())
        val name = NamePrompt(context[ScoreObjectRegistry], "Name for object", initialName)
            .showDialog(scene.window, anchor) ?: return null
        return SoundProcess(reactiveVariable(instrument), controls).withName(name)
    }
}