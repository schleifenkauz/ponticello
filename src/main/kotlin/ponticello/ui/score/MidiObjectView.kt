package ponticello.ui.score

import fxutils.*
import fxutils.controls.IntSpinner
import fxutils.prompt.*
import fxutils.undo.UndoManager
import hextant.context.Context
import hextant.core.editor.defaultState
import hextant.fx.ModifierKeyTracker
import hextant.serial.EditorRoot
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleDoubleProperty
import javafx.collections.FXCollections.observableList
import javafx.geometry.HorizontalDirection
import javafx.geometry.Point2D
import javafx.geometry.VerticalDirection
import javafx.scene.Cursor
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.paint.Color
import javafx.scene.shape.Line
import javafx.scene.shape.Rectangle
import ponticello.impl.*
import ponticello.model.obj.MidiInstrument
import ponticello.model.obj.project
import ponticello.model.obj.withName
import ponticello.model.project.uiState
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.score.MidiObject
import ponticello.model.score.ObjectPosition
import ponticello.model.score.ScoreObject
import ponticello.model.score.ScoreObjectInstance
import ponticello.sc.Identifier
import ponticello.sc.editor.EventDictionaryEditor
import ponticello.ui.impl.makeSubWindow
import ponticello.ui.impl.setupDraggingAndResizing
import ponticello.ui.impl.showDialog
import reaktive.value.binding.and
import reaktive.value.binding.binding
import reaktive.value.binding.or
import reaktive.value.fx.asObservableValue
import reaktive.value.fx.asReactiveValue
import reaktive.value.now
import reaktive.value.reactiveVariable
import kotlin.math.roundToInt

class MidiObjectView(override val obj: MidiObject, inst: ScoreObjectInstance) : ScoreObjectView(inst) {
    private val noteRects = mutableMapOf<MidiObject.Note, BorderPane>()
    private val orientationLines = mutableListOf<Line>()
    private val blackKeys = mutableListOf<Rectangle>()
    private val pixelsPerPitch get() = prefHeight / (obj.highestPitch - obj.lowestPitch + 1)
    private val cursor = Rectangle(10.0, pixelsPerPitch) styleClass "note-cursor"
    private val lowestPitchLabel = Label(MidiPitch(obj.lowestPitch).getNoteName())
    private val highestPitchLabel = Label(MidiPitch(obj.highestPitch).getNoteName())

    private fun getY(pitch: Int) = (obj.highestPitch - pitch) * pixelsPerPitch

    private fun getMidiNote(y: Double): Int = ((height - y) / pixelsPerPitch).roundToInt() + obj.lowestPitch - 1

    fun addedNote(note: MidiObject.Note) {
        val rect = BorderPane() styleClass "note-object"
        rect.backgroundProperty().bind(Bindings.createObjectBinding({
            val background = backgroundColor.now
            if (rect.isFocused)
                background(background.interpolate(background.invert(), 0.8))
            else background(background.invert())
        }, backgroundColor.asObservableValue(), rect.focusedProperty()))
        rect.borderProperty().bind(Bindings.createObjectBinding({
            if (rect.isHover) {
                val background = backgroundColor.now.invert()
                solidBorder(background.darker(), 2.0)
            } else solidBorder(Color.TRANSPARENT, 2.0)
        }, backgroundColor.asObservableValue(), rect.hoverProperty()))
        noteRects[note] = rect
        setupNoteObjectEvents(rect, note)
        updateNoteDisplay(rect, note)
        children.add(rect)
    }

    private fun updateNoteDisplay(rect: Region, note: MidiObject.Note) {
        rect.layoutX = getWidth(note.onset)
        rect.layoutY = getY(note.midinote)
        rect.prefWidth = getWidth(note.duration)
        rect.prefHeight = pixelsPerPitch
    }

    private fun snapToGrid(x: Double, y: Double): Decimal {
        val pos = ObjectPosition(getTime(x), getScoreY(y))
        return parentPane.root.snapToGrid(pos + absolutePosition).time - absolutePosition.time
    }

    private fun setupNoteObjectEvents(rect: Region, note: MidiObject.Note) {
        rect.isFocusTraversable = true
        rect.setupDraggingAndResizing(
            context = context,
            canUserChangeWidth = true, canUserChangeHeight = false,
            drag = { toX, toY ->
                val t = snapToGrid(toX, toY)
                note.onset = t.coerceIn(zero, obj.duration - note.duration)
                note.midinote = getMidiNote(toY).coerceIn(obj.pitchRange)
                parentPane.markT(instance.start + t)
            },
            resize = { old, dx, dy, cursor, _ ->
                when (cursor) {
                    Cursor.W_RESIZE -> {
                        val t = snapToGrid(old.minX + dx, old.minY + dy)
                        val oldTime = note.onset
                        note.onset = t.coerceAtLeast(zero)
                        note.duration += (oldTime - note.onset)
                        parentPane.markT(instance.start + t)
                    }

                    Cursor.E_RESIZE -> {
                        val t = snapToGrid(old.maxX + dx - old.minX, old.maxY + dy)
                        note.duration = t.coerceIn(zero, obj.duration - note.onset)
                        parentPane.markT(instance.start + note.onset + note.duration)
                    }
                }
            },
        )
        rect.addEventHandler(MouseEvent.ANY) { ev ->
            when (ev.eventType) {
                MouseEvent.MOUSE_ENTERED -> children.remove(cursor)
                MouseEvent.MOUSE_EXITED -> if (cursor !in children && !ev.isPrimaryButtonDown && !ev.isSecondaryButtonDown) {
                    try {
                        children.add(cursor)
                    } catch (e: IllegalArgumentException) { //this occurs if the cursor was already added, how is this possible?
                        println("Attempt to duplicate cursor...")
                    }
                }

                MouseEvent.MOUSE_CLICKED -> when {
                    ev.button == MouseButton.SECONDARY -> obj.removeNote(note)
                    ev.clickCount >= 2 -> showEventDictionaryEditor(note.eventDictionary)
                    else -> rect.requestFocus()
                }
            }
            ev.consume()
        }
        rect.registerShortcuts(KeyEvent.KEY_PRESSED) {
            on("LEFT") {
                val delta = getDeltaT(HorizontalDirection.LEFT)
                if (note.onset + delta >= zero) note.onset += delta
            }
            on("RIGHT") {
                val delta = getDeltaT(HorizontalDirection.RIGHT)
                if (note.onset + delta + note.duration <= obj.duration) note.onset += delta
            }
            on("DOWN") {
                if (note.midinote - 1 >= obj.lowestPitch) note.midinote--
            }
            on("UP") {
                if (note.midinote + 1 <= obj.highestPitch) note.midinote++
            }
            on("DELETE") {
                obj.removeNote(note)
            }
        }
    }

    private fun showEventDictionaryEditor(dictionary: EditorRoot<EventDictionaryEditor>) {
        val control = dictionary.control
        val window = makeSubWindow(control, "Note properties", context)
        window.resize(300.0, 200.0)
        window.show()
    }

    fun updatedNote(note: MidiObject.Note) {
        val rect = noteRects[note] ?: return Logger.error("Note $note was note displayed in $this")
        updateNoteDisplay(rect, note)
    }

    fun removedNote(note: MidiObject.Note) {
        val rect = noteRects.remove(note) ?: return Logger.error("Note $note was note displayed in $this")
        children.remove(rect)
    }

    private fun drawOrientationLines() {
        children.removeAll(orientationLines)
        orientationLines.clear()
        for (pitch in obj.lowestPitch until obj.highestPitch) {
            val line = Line() styleClass "pitch-line"
            val y = getY(pitch)
            line.startY = y
            line.endY = y
            line.endX = prefWidth
            orientationLines.add(line)
        }
        children.addAll(orientationLines)
    }

    private fun shadeBlackKeys() {
        children.removeAll(blackKeys)
        blackKeys.clear()
        for (pitch in obj.pitchRange) {
            if (MidiPitch(pitch).isBlackKey()) {
                val shade = Rectangle(0.0, getY(pitch), prefWidth, pixelsPerPitch)
                shade.fill = Color.rgb(0, 0, 0, 0.3)
                shade.viewOrder = 100.0
                blackKeys.add(shade)
            }
        }
        children.addAll(blackKeys)
    }

    fun updatedPitchRange() {
        drawOrientationLines()
        shadeBlackKeys()
        lowestPitchLabel.text = MidiPitch(obj.lowestPitch).getNoteName()
        highestPitchLabel.text = MidiPitch(obj.highestPitch).getNoteName()
    }

    override fun initialize() {
        super.initialize()
        drawOrientationLines()
        shadeBlackKeys()
        listenForMouseEvents()
    }

    private inner class InstrumentSelectorPopup : SearchableListView<MidiInstrument>("Select instrument") {
        override fun options(): List<MidiInstrument> = MidiInstrument.getOptions(context.project)

        override fun createCell(option: MidiInstrument): Region {
            val (type, name) = when (option) {
                is MidiInstrument.SynthDef -> Pair("SynthDef", option.reference.getName())
                is MidiInstrument.VST -> {
                    val flow = option.flow.force()
                    Pair("VST: ${flow.pluginName}", flow.name.now)
                }

                MidiInstrument.None -> throw AssertionError()
            }
            return HBox(Label(name), infiniteSpace(), Label(type))
        }

        override fun extractText(option: MidiInstrument): String = when (option) {
            is MidiInstrument.SynthDef -> option.reference.getName()
            is MidiInstrument.VST -> option.flow.getName()
            MidiInstrument.None -> throw AssertionError()
        }
    }

    override fun setupDetailPane(pane: DetailPane) {
        val instrumentSelector = InstrumentSelectorPopup().selectorButton(
            obj.instrument, undoManager = context[UndoManager], actionDescription = "Select MIDI instrument"
        )
        pane.addItem("Instrument: ", instrumentSelector)
        pane.addItem("Color:", this.colorPicker)
        val transposeButton = button("Transpose") { showTransposeDialog() }
        pane.addItem(
            "Pitch range: ", HBox(
                lowestPitchLabel, Label(" - "), highestPitchLabel,
                hspace(50.0), transposeButton
            ).centerChildren()
        )
        pane.addItem("Latency (ms): ", IntSpinner(obj.latencyMs, -200..200, 5))
        pane.addLargeItem("Event dictionary", obj.eventDictionary.control)
    }

    override fun adjustVertical(direction: VerticalDirection, resizeMode: ScoreObject.ResizeMode?) {
        var deltaY = obj.height / (obj.highestPitch - obj.lowestPitch + 1)
        if (direction == VerticalDirection.UP) deltaY *= -1
        adjustVertical(resizeMode, deltaY)
    }

    fun showTransposeDialog() {
        val semitones = IntegerPrompt("Transpose by semitones", 0, -48..48)
            .showDialog(context) ?: return
        obj.transpose(semitones)
    }

    private fun listenForMouseEvents() {
        cursor.viewOrder = 100.0
        cursor.visibleProperty().bind(
            hoverProperty().asReactiveValue().and(
                ModifierKeyTracker.isAltDown or cursor.opacityProperty().isEqualTo(SimpleDoubleProperty(1.0))
                    .asReactiveValue()
            ).asObservableValue()
        )
        cursor.opacity = CURSOR_OPACITY
        children.add(cursor)
        cursor.fillProperty().bind(
            binding(
                backgroundColor, cursor.opacityProperty().asReactiveValue()
            ) { background, opacity ->
                background.invert().deriveColor(0.0, 1.0, 1.0, opacity.toDouble())
            }.asObservableValue()
        )
        var mousePressed: Point2D? = null
        addEventHandler(MouseEvent.ANY) { ev ->
            when (ev.eventType) {
                MouseEvent.MOUSE_MOVED -> {
                    cursor.x = getX(snapToGrid(ev.x, ev.y))
                    cursor.y = ev.y.snap(pixelsPerPitch.toDecimal()).toDouble()
                }

                MouseEvent.MOUSE_PRESSED -> {
                    if (ev.modifiers == setOf(Alt)) {
                        mousePressed = Point2D(ev.x, ev.y)
                        cursor.opacity = 1.0
                        ev.consume()
                    }
                }

                MouseEvent.MOUSE_DRAGGED -> if (cursor.opacity == 1.0) {
                    val noteEnd = snapToGrid(ev.x, ev.y)
                    val noteStart = getTime(cursor.x)
                    cursor.width = getWidth(noteEnd - noteStart)
                    parentPane.markT(instance.start + noteEnd)
                    ev.consume()

                }

                MouseEvent.MOUSE_RELEASED -> if (cursor.opacity == 1.0) {
                    val time = getDuration(cursor.x)
                    val duration =
                        if (mousePressed != null && mousePressed!!.distance(ev.x, ev.y) > 0.1) {
                            getDuration(cursor.width)
                        } else {
                            val meter = parentPane.getNearestGrid(instance.position)?.second
                            val settings = context.project.uiState
                            val snapOption =
                                if (settings.snapEnabled.now) settings.snapOption.now
                                else null
                            snapOption?.let { meter?.getDuration(it) } ?: getDuration(cursor.width)
                        }
                    val midinote = getMidiNote(cursor.y)
                    val note = MidiObject.Note.create(time, duration, midinote)
                    obj.addNote(note)
                    cursor.opacity = CURSOR_OPACITY
                    cursor.width = 10.0
                    ev.consume()
                }
            }
        }
    }

    override fun rescale() {
        super.rescale()
        drawOrientationLines()
        shadeBlackKeys()
        for ((note, rect) in noteRects) {
            updateNoteDisplay(rect, note)
        }
        cursor.height = pixelsPerPitch
    }

    companion object {
        private const val CURSOR_OPACITY = 0.6

        fun createNewMidiObjectDialog(instr: MidiInstrument, context: Context): Prompt<MidiObject?, *> =
            compoundPrompt("Configure MIDI object", labelWidth = 200.0) {
                val defaultName = context[ScoreObjectRegistry].availableName("midi")
                val nameField = TextField(defaultName) named "Object name"
                val rootPitchSelector =
                    ComboBox(observableList(MidiPitch.allPitchClasses())) named "Root pitch class"
                rootPitchSelector.value = MidiPitch(0)
                val registerSpinner = IntSpinner(0, 10, 4).minColumns(2) named "Base register"
                val octaves = IntSpinner(1, 12, 2).minColumns(2) named "Octaves"
                onConfirm {
                    val name = nameField.text
                    if (!Identifier.isValid(name) || context[ScoreObjectRegistry].has(name)) return@onConfirm null
                    val lowestPitch = rootPitchSelector.value.step + 12 * registerSpinner.value()
                    val highestPitch = lowestPitch + 12 * octaves.value()
                    val notes = mutableListOf<MidiObject.Note>()
                    val eventDictionary = EditorRoot(EventDictionaryEditor().defaultState())
                    MidiObject(
                        reactiveVariable(instr),
                        lowestPitch, highestPitch,
                        eventDictionary, notes
                    ).withName(name)
                }
            }
    }
}