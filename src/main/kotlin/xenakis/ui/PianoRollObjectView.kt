package xenakis.ui

import hextant.undo.UndoManager
import javafx.scene.Cursor
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.shape.Line
import javafx.scene.shape.Rectangle
import reaktive.value.ReactiveValue
import reaktive.value.binding.binding
import reaktive.value.binding.map
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.model.PianoRollObject

class PianoRollObjectView(private val obj: PianoRollObject) : ScoreObjectView(obj) {
    private val noteRects = mutableMapOf<PianoRollObject.Note, Rectangle>()
    private val orientationLines = mutableListOf<Line>()
    private val notePane = Pane()
    private val cursor = Rectangle(10.0, obj.pixelsPerPitch) styleClass "note-cursor"
    private val cursorOpacity = reactiveVariable(CURSOR_OPACITY)

    fun addedNote(note: PianoRollObject.Note) {
        val rect = Rectangle() styleClass "note-object"
        rect.fillProperty().bind(backgroundColor.map { c -> c.invert() }.asObservableValue())
        noteRects[note] = rect
        setupNoteObjectEvents(rect, note)
        updateNoteDisplay(rect, note)
        notePane.children.add(rect)
    }

    private fun updateNoteDisplay(rect: Rectangle, note: PianoRollObject.Note) {
        rect.x = pane.getWidth(note.time)
        rect.y = obj.getY(note.midinote)
        rect.width = pane.getWidth(note.duration)
        rect.height = obj.pixelsPerPitch
    }

    private fun setupNoteObjectEvents(rect: Rectangle, note: PianoRollObject.Note) {
        rect.isFocusTraversable = true
        rect.setupDragging(
            onPressed = { context[UndoManager].beginCompoundEdit("Edit midi note") },
            onReleased = { context[UndoManager].finishCompoundEdit("Edit midi note") }
        ) { _, _, old, dx, dy ->
            note.time = pane.getDuration(old.minX + dx)
            note.midinote = obj.getMidiNote(old.minY + dy)
        }
        rect.addEventHandler(MouseEvent.ANY) { ev ->
            if (context[XenakisUI].toolSelector.selected.value != ToolSelector.Tool.PianoRoll) return@addEventHandler
            when (ev.eventType) {
                MouseEvent.MOUSE_ENTERED -> notePane.children.remove(cursor)
                MouseEvent.MOUSE_EXITED -> if (cursor !in notePane.children) notePane.children.add(cursor)
            }
            ev.consume()
        }
        rect.setOnMouseClicked { ev ->
            when {
                ev.button == MouseButton.SECONDARY -> obj.removeNote(note)
            }
        }
    }

    fun updatedNote(note: PianoRollObject.Note) {
        val rect = noteRects[note] ?: error("Note $note was note displayed in $this")
        updateNoteDisplay(rect, note)
    }

    fun removedNote(note: PianoRollObject.Note) {
        val rect = noteRects.remove(note) ?: error("Note $note was note displayed in $this")
        notePane.children.remove(rect)
    }

    private fun drawOrientationLines() {
        notePane.children.removeAll(orientationLines)
        orientationLines.clear()
        for (pitch in obj.pitchRange) {
            val line = Line() styleClass "pitch-line"
            val y = obj.getY(pitch)
            line.startY = y
            line.endY = y
            line.endX = prefWidth
            orientationLines.add(line)
            notePane.children.add(line)
        }
    }

    fun updatedPitchRange() {
        drawOrientationLines()
    }

    override fun initialize(parent: ScorePane) {
        super.initialize(parent)
        children.add(notePane)
        drawOrientationLines()
        widthProperty().addListener { _ -> rescale() }
        heightProperty().addListener { _ -> rescale() }
        listenForMouseEvents()
    }

    private fun listenForMouseEvents() {
        cursor.viewOrder = 100.0
        cursor.fillProperty().bind(binding(backgroundColor, cursorOpacity) { background, opacity ->
            background.invert().deriveColor(0.0, 1.0, 1.0, opacity)
        }.asObservableValue())
        notePane.addEventHandler(MouseEvent.ANY) { ev ->
            if (context[XenakisUI].toolSelector.selected.value != ToolSelector.Tool.PianoRoll) return@addEventHandler
            ev.consume()
            when (ev.eventType) {
                MouseEvent.MOUSE_ENTERED -> {
                    if (cursor !in notePane.children) notePane.children.add(cursor)
                }

                MouseEvent.MOUSE_MOVED -> {
                    cursor.x = (ev.x - 10.0).snap(pane.timeSnap)
                    cursor.y = ev.y.snap(obj.pixelsPerPitch)
                }

                MouseEvent.MOUSE_EXITED -> {
                    notePane.children.remove(cursor)
                }

                MouseEvent.MOUSE_PRESSED -> {
                    cursorOpacity.now = 1.0
                }

                MouseEvent.MOUSE_DRAGGED -> {
                    cursor.width = (ev.x - cursor.x).snap(pane.timeSnap)
                }

                MouseEvent.MOUSE_RELEASED -> {
                    val time = pane.getDuration(cursor.x)
                    val duration = pane.getDuration(cursor.width)
                    val midinote = obj.getMidiNote(cursor.y)
                    val note = PianoRollObject.Note(time, duration, midinote, _velocity = 60)
                    obj.addNote(note)
                    cursorOpacity.now = CURSOR_OPACITY
                    cursor.width = 10.0
                }
            }
        }
    }

    override fun rescale() {
        super.rescale()
        drawOrientationLines()
        for ((note, rect) in noteRects) {
            updateNoteDisplay(rect, note)
        }
        cursor.height = obj.pixelsPerPitch
    }

    override val defaultBackgroundColor: ReactiveValue<Color>
        get() = obj.instrument.get().color

    companion object {
        private const val CURSOR_OPACITY = 0.6
    }
}