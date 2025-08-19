package ponticello.ui.score

import fxutils.*
import fxutils.prompt.SimpleSearchableListView
import hextant.context.Context
import hextant.context.compoundEdit
import hextant.core.editor.defaultState
import hextant.serial.EditorRoot
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import javafx.scene.robot.Robot
import javafx.scene.shape.Rectangle
import ponticello.model.obj.*
import ponticello.model.registry.*
import ponticello.model.score.*
import ponticello.sc.editor.CodeBlockEditor
import ponticello.ui.controls.NamePrompt
import ponticello.ui.registry.SimpleSearchableRegistryView
import reaktive.value.now
import reaktive.value.reactiveVariable

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

    private fun createTaskObject(ev: KeyEvent) {
        val defaultName = context[ScoreObjectRegistry].availableName("task")
        val name = NamePrompt(context[ScoreObjectRegistry], "Task name", defaultName)
            .showDialog(ev) ?: return
        val code = EditorRoot(CodeBlockEditor().defaultState())
        val obj = TaskObject(code).withName(name)
        val p = screenToLocal(Robot().mousePosition)
        if (!boundsInLocal.contains(p)) return
        val (t, y) = snapToGrid(p.x, p.y)
        score.addObject(obj, t, y, autoSelect = true)
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

    override fun mouseReleased(ev: MouseEvent) {
        ev.consume()
        val selection = selectedArea
        if (selection == null || selection.isEmpty()) return
        if (selection.isTimeSelection) {
            selection.rect.requestFocus()
            return
        }
        when (ev.modifiers) {
            setOf(Alt) -> {
                val containedViews = viewsInside(selection.rect.boundsInParent)
                selector.deselectAll()
                if (containedViews.isEmpty()) {
                    addNewObject(selection)
                } else {
                    addNewGroup(ev, selection, containedViews)
                }
            }
            else -> super.mouseReleased(ev)
        }
    }

    private fun addNewGroup(ev: MouseEvent, selection: RectangleSelection, containedViews: List<ScoreObjectView>) {
        val name = context[ScoreObjectRegistry].nameForGroup(ev) ?: return
        context.compoundEdit("Add object group") {
            val subScore = Score(mutableListOf())
            val groupObj = ScoreObjectGroup(subScore).withName(name)
            val inst = addObject(groupObj, selection)
            val relativePosition = -inst.position
            for (view in containedViews) {
                view.instance.moveInto(subScore, relativePosition, recurse = ev.isShiftDown)
            }
        }
    }

    private fun addNewObject(selection: RectangleSelection) {
        val availableOptions = context[InstrumentRegistry].map(NewObjectOption::Process) +
                MidiInstrument.getOptions(context.project).map(NewObjectOption::MIDI) +
                context[MeterRegistry].map(NewObjectOption::TempoGrid) +
                listOf(NewObjectOption.Group, NewObjectOption.NewTempoGrid)
        val popup = SimpleSearchableListView(availableOptions, "Add score object")
        val anchor =
            localToScreen(selection.rect.boundsInParent.centerX, selection.rect.boundsInParent.minY)
        val option = popup.showPopup(anchor, scene.window) ?: return
        val obj = if (option is NewObjectOption.MIDI) {
            MidiObjectView.createNewMidiObjectDialog(option.def, context)
                .showDialog(scene.window, anchor) ?: return
        } else {
            val initialName = option.defaultName(context[ScoreObjectRegistry])
            val name = NamePrompt(context[ScoreObjectRegistry], "Name for object", initialName)
                .showDialog(scene.window, anchor) ?: return
            val obj = createNewObject(option, selection.rect, name) ?: return
            obj.withName(name)
        }
        addObject(obj, selection)
    }

    private fun createNewObject(option: NewObjectOption, rect: Rectangle, name: String): ScoreObject? {
        return when (option) {
            is NewObjectOption.Process -> {
                val defaultBus = (associatedObject as? ScoreObjectGroup)?.defaultBusRef?.now?.get()
                val anchor = localToScreen(rect.middlePoint)
                val controls = getInitialControls(option.def, context, defaultBus, anchor) ?: return null
                val instrument = option.def.reference()
                SoundProcess(reactiveVariable(instrument), controls)
            }

            is NewObjectOption.MIDI -> throw AssertionError("Handled before")
            is NewObjectOption.TempoGrid -> TempoGridObject(option.meter.reference())

            NewObjectOption.NewTempoGrid -> {
                val newMeter = MeterObject.create(60, 4, 4).withName(name)
                context[MeterRegistry].add(newMeter)
                TempoGridObject(newMeter.reference())
            }

            NewObjectOption.Group -> ScoreObjectGroup(Score(mutableListOf()))
        }
    }

    private fun addObject(obj: ScoreObject, rect: RectangleSelection): ScoreObjectInstance {
        val registry = context[ScoreObjectRegistry]
        if (obj in registry) registry.add(obj)
        val inst = rect.createInstance(obj)
        obj.liveConfig.yPosition.set(this.absolutePosition.y + inst.y)
        score.addObject(inst, autoSelect = true)
        return inst
    }

    private sealed class NewObjectOption {
        override fun toString() = when (this) {
            is Process -> when (def) {
                is SynthDefObject -> "Synth: ${def.name.now}"
                is ProcessDefObject -> "Process: ${def.name.now}"
                else -> throw AssertionError()
            }

            is MIDI -> "MIDI: ${def.getName()}"
            is Group -> "Group"
            is TempoGrid -> "Tempo grid: ${meter.name.now}"
            is NewTempoGrid -> "New tempo grid"
        }

        fun defaultName(registry: ScoreObjectRegistry): String = when (this) {
            is MIDI -> registry.availableName("${def.getName()}_midi")
            is Process -> registry.availableName(def.name.now)
            is TempoGrid -> registry.availableName(meter.name.now)
            is NewTempoGrid -> registry.availableName("tempo")
            Group -> registry.availableName("group")
        }

        class Process(val def: InstrumentObject) : NewObjectOption()
        class MIDI(val def: MidiInstrument) : NewObjectOption()
        class TempoGrid(val meter: MeterObject) : NewObjectOption()
        object Group : NewObjectOption()
        object NewTempoGrid : NewObjectOption()
    }
}