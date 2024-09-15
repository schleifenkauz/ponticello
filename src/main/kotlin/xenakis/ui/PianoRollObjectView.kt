package xenakis.ui

import bundles.createBundle
import hextant.fx.initHextantScene
import hextant.serial.EditorRoot
import javafx.scene.Cursor
import javafx.scene.control.Label
import javafx.scene.control.Spinner
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.Region
import javafx.scene.paint.Color
import javafx.scene.shape.Line
import javafx.scene.shape.Rectangle
import reaktive.value.ReactiveValue
import reaktive.value.binding.binding
import reaktive.value.binding.flatMap
import reaktive.value.binding.map
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.MidiPitch
import xenakis.impl.Point
import xenakis.model.InstrumentObject
import xenakis.model.PianoRollObject
import xenakis.model.ScoreObjectInstance
import xenakis.sc.editor.EventDictionaryEditor
import xenakis.sc.view.ObjectSelectorControl

class PianoRollObjectView(inst: ScoreObjectInstance, private val obj: PianoRollObject) : ScoreObjectView(inst) {
    private val noteRects = mutableMapOf<PianoRollObject.Note, BorderPane>()
    private val orientationLines = mutableListOf<Line>()
    private val blackKeys = mutableListOf<Rectangle>()
    private val notePane = Pane()
    private val cursor = Rectangle(10.0, obj.pixelsPerPitch) styleClass "note-cursor"
    private val cursorOpacity = reactiveVariable(CURSOR_OPACITY)

    fun addedNote(note: PianoRollObject.Note) {
        val rect = BorderPane() styleClass "note-object"
        rect.backgroundProperty().bind(backgroundColor.map { c -> c.invert() }.asObservableValue().map(::background))
        noteRects[note] = rect
        setupNoteObjectEvents(rect, note)
        updateNoteDisplay(rect, note)
        notePane.children.add(rect)
    }

    private fun updateNoteDisplay(rect: Region, note: PianoRollObject.Note) {
        rect.layoutX = pane.getWidth(note.onset)
        rect.layoutY = obj.getY(note.midinote)
        rect.prefWidth = pane.getWidth(note.duration)
        rect.prefHeight = obj.pixelsPerPitch
    }

    private fun snapToGrid(x: Double, y: Double): Point {
        var p = localToScreen(x, y)
        p = pane.screenToLocal(p)
        p = pane.snapToGrid(p.x, p.y).point2d
        p = pane.localToScreen(p)
        p = screenToLocal(p)
        return Point(p)
    }

    private fun setupNoteObjectEvents(rect: Region, note: PianoRollObject.Note) {
        rect.isFocusTraversable = true
        rect.setupDraggingAndResizing(
            pane,
            canUserChangeWidth = true, canUserChangeHeight = false, ToolSelector.Tool.PianoRoll,
            relocateBy = { old, dx, dy ->
                val (x, y) = snapToGrid(old.minX + dx, old.minY + dy)
                note.onset = pane.getDuration(x).coerceIn(0.0, obj.duration - note.duration)
                note.midinote = obj.getMidiNote(y).coerceIn(obj.pitchRange)
            },
            resize = { old, dx, dy, cursor, _ ->
                when (cursor) {
                    Cursor.W_RESIZE -> {
                        val (x) = snapToGrid(old.minX + dx, old.minY + dy)
                        val oldTime = note.onset
                        note.onset = pane.getDuration(x).coerceAtLeast(0.0)
                        note.duration += (oldTime - note.onset)
                    }

                    Cursor.E_RESIZE -> {
                        val (x) = snapToGrid(old.maxX + dx, old.maxY + dy)
                        note.duration = pane.getDuration(x - old.minX).coerceIn(0.0, obj.duration - note.onset)
                    }
                }
            }
        )
        rect.addEventHandler(MouseEvent.ANY) { ev ->
            if (context[XenakisUI].toolSelector.selected.value != ToolSelector.Tool.PianoRoll) return@addEventHandler
            when (ev.eventType) {
                MouseEvent.MOUSE_ENTERED -> notePane.children.remove(cursor)
                MouseEvent.MOUSE_EXITED -> if (cursor !in notePane.children && !ev.isPrimaryButtonDown && !ev.isSecondaryButtonDown) {
                    notePane.children.add(cursor)
                }

                MouseEvent.MOUSE_CLICKED -> {
                    if (ev.button == MouseButton.SECONDARY) obj.removeNote(note)
                    else if (ev.clickCount >= 2) {
                        showEventDictionaryEditor(note.eventDictionary)
                    }
                }
            }
            ev.consume()
        }
    }

    private fun showEventDictionaryEditor(dictionary: EditorRoot<EventDictionaryEditor>) {
        val control = dictionary.control
        val window = SubWindow(control, "Note properties", context, type = SubWindow.Type.Popup)
        window.scene.initHextantScene(context, applyStyle = false)
        window.resize(500.0, 500.0)
        window.show()
    }

    override fun beforeResize(ev: MouseEvent, cursor: Cursor) {
        super.beforeResize(ev, cursor)
        obj.pixelsPerPitchBeforeResize = obj.pixelsPerPitch
    }

    fun updatedNote(note: PianoRollObject.Note) {
        val rect = noteRects[note] ?: return alertError("Note $note was note displayed in $this")
        updateNoteDisplay(rect, note)
    }

    fun removedNote(note: PianoRollObject.Note) {
        val rect = noteRects.remove(note) ?: return alertError("Note $note was note displayed in $this")
        notePane.children.remove(rect)
    }

    private fun drawOrientationLines() {
        notePane.children.removeAll(orientationLines)
        orientationLines.clear()
        for (pitch in obj.lowestPitch until obj.highestPitch) {
            val line = Line() styleClass "pitch-line"
            val y = obj.getY(pitch)
            line.startY = y
            line.endY = y
            line.endX = prefWidth
            orientationLines.add(line)
        }
        notePane.children.addAll(orientationLines)
    }

    private fun shadeBlackKeys() {
        notePane.children.removeAll(blackKeys)
        blackKeys.clear()
        for (pitch in obj.pitchRange) {
            if (MidiPitch(pitch).isBlackKey()) {
                val shade = Rectangle(0.0, obj.getY(pitch), prefWidth, obj.pixelsPerPitch)
                shade.fill = Color.rgb(0, 0, 0, 0.3)
                shade.viewOrder = 100.0
                blackKeys.add(shade)
            }
        }
        notePane.children.addAll(blackKeys)
    }

    fun updatedPitchRange() {
        drawOrientationLines()
        shadeBlackKeys()
    }

    override fun initialize(parent: ScorePane) {
        super.initialize(parent)
        children.add(notePane)
        drawOrientationLines()
        shadeBlackKeys()
        addAction(Icon.Transpose, action = "Transpose") {
            showTransposeDialog()
        }
        listenForMouseEvents()
    }

    override fun DetailPane.setupDetailPane() {
        val instrumentSelector = ObjectSelectorControl(obj.instrumentSelector, createBundle())
        addItem("Instrument: ", instrumentSelector)
        addLargeItem("Event dictionary", obj.eventDictionary.control)

    }

    private fun showTransposeDialog() {
        val spinner = Spinner<Int>(-36, +36, 0, 1)
        val layout = HBox(5.0, Label("Semitones"), spinner).centerChildrenVertically()
        val deltaPitch = layout.showDialog("Transpose", context) { spinner.value } ?: return
        obj.transpose(deltaPitch)
    }

    private fun listenForMouseEvents() {
        cursor.viewOrder = 100.0
        cursor.fillProperty().bind(binding(backgroundColor, cursorOpacity) { background, opacity ->
            background.invert().deriveColor(0.0, 1.0, 1.0, opacity)
        }.asObservableValue())
        notePane.addEventHandler(MouseEvent.ANY) { ev ->
            val selectedTool = context[XenakisUI].toolSelector.selected.value
            if (selectedTool == ToolSelector.Tool.AddTime && ev.eventType == MouseEvent.MOUSE_CLICKED) {
                val (x, _) = snapToGrid(ev.x, ev.y)
                val position = pane.getDuration(x)
                showNumberPrompt("How much time to add", 0.0..1000.0, 10.0, context) { amount ->
                    obj.addTime(position, amount)
                }
            }
            if (selectedTool != ToolSelector.Tool.PianoRoll) {
                notePane.children.remove(cursor)
                return@addEventHandler
            }
            when (ev.eventType) {
                MouseEvent.MOUSE_ENTERED -> {
                    if (cursor !in notePane.children && !ev.isPrimaryButtonDown) notePane.children.add(cursor)
                }

                MouseEvent.MOUSE_MOVED -> {
                    cursor.x = snapToGrid(ev.x, ev.y).x
                    cursor.y = ev.y.snap(obj.pixelsPerPitch)
                    if (cursor.y > prefHeight) notePane.children.remove(cursor)
                }

                MouseEvent.MOUSE_EXITED -> {
                    notePane.children.remove(cursor)
                    ev.consume()
                }

                MouseEvent.MOUSE_PRESSED -> {
                    cursorOpacity.now = 1.0
                    ev.consume()
                }

                MouseEvent.MOUSE_DRAGGED -> {
                    cursor.width = snapToGrid(ev.x - cursor.x, ev.y).x
                    ev.consume()
                }

                MouseEvent.MOUSE_RELEASED -> {
                    val time = pane.getDuration(cursor.x)
                    val duration = pane.getDuration(cursor.width)
                    val midinote = obj.getMidiNote(cursor.y)
                    val note = PianoRollObject.Note.create(context, time, duration, midinote)
                    obj.addNote(note)
                    cursorOpacity.now = CURSOR_OPACITY
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
        cursor.height = obj.pixelsPerPitch
    }

    override val defaultBackgroundColor: ReactiveValue<Color>
        get() = obj.instrumentSelector.selected.flatMap { instr -> instr.get<InstrumentObject>().color }

    companion object {
        private const val CURSOR_OPACITY = 0.6
    }
}