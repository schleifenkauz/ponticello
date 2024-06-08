package xenakis.ui

import bundles.createBundle
import hextant.fx.initHextantScene
import hextant.serial.EditorRoot
import javafx.scene.Cursor
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Pane
import javafx.scene.layout.Region
import javafx.scene.paint.Color
import javafx.scene.shape.Line
import javafx.scene.shape.Rectangle
import reaktive.value.ReactiveValue
import reaktive.value.binding.binding
import reaktive.value.binding.map
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.MidiPitch
import xenakis.impl.Point
import xenakis.model.PianoRollObject
import xenakis.sc.editor.EventDictionaryEditor
import xenakis.sc.view.ObjectSelectorControl
import kotlin.math.roundToInt

class PianoRollObjectView(private val obj: PianoRollObject) : ScoreObjectView(obj) {
    private val noteRects = mutableMapOf<PianoRollObject.Note, BorderPane>()
    private val orientationLines = mutableListOf<Line>()
    private val blackKeys = mutableListOf<Rectangle>()
    private val notePane = Pane()
    private val cursor = Rectangle(10.0, obj.pixelsPerPitch) styleClass "note-cursor"
    private val cursorOpacity = reactiveVariable(CURSOR_OPACITY)
    private var pixelsPerPitchBeforeResize: Double = obj.pixelsPerPitch

    fun addedNote(note: PianoRollObject.Note) {
        val rect = BorderPane() styleClass "note-object"
        rect.backgroundProperty().bind(backgroundColor.map { c -> c.invert() }.asObservableValue().map(::background))
        noteRects[note] = rect
        setupNoteObjectEvents(rect, note)
        updateNoteDisplay(rect, note)
        notePane.children.add(rect)
    }

    private fun updateNoteDisplay(rect: Region, note: PianoRollObject.Note) {
        rect.layoutX = pane.getWidth(note.time)
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
            context,
            canUserChangeWidth = true, canUserChangeHeight = false, ToolSelector.Tool.PianoRoll,
            relocateBy = { old, dx, dy ->
                val (x, y) = snapToGrid(old.minX + dx, old.minY + dy)
                note.time = pane.getDuration(x).coerceIn(0.0, obj.duration - note.duration)
                note.midinote = obj.getMidiNote(y).coerceIn(obj.pitchRange)
            },
            resize = { old, dx, dy, cursor, _ ->
                when (cursor) {
                    Cursor.W_RESIZE -> {
                        val (x) = snapToGrid(old.minX + dx, old.minY + dy)
                        val oldTime = note.time
                        note.time = pane.getDuration(x).coerceAtLeast(0.0)
                        note.duration += (oldTime - note.time)
                    }

                    Cursor.E_RESIZE -> {
                        val (x) = snapToGrid(old.maxX + dx, old.maxY + dy)
                        note.duration = pane.getDuration(x - old.minX).coerceIn(0.0, obj.duration - note.time)
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
        pixelsPerPitchBeforeResize = obj.pixelsPerPitch
    }

    override fun resizeObject(width: Double, height: Double, ev: MouseEvent, cursor: Cursor) {
        val dur = pane.getDuration(width)
        if (ev.isShiftDown) {
            val horizontalRatio = dur / obj.duration
            obj.duration = dur
            obj.height = height
            for (note in obj.notes()) {
                note.time *= horizontalRatio
                note.duration *= horizontalRatio
            }
        } else {
            var minDur = 0.0
            var minHeight = 0.0
            val notes = obj.notes()
            if (notes.isNotEmpty()) {
                minDur =
                    if (cursor.resizeFromLeft) obj.duration - notes.minOf { n -> n.time }
                    else notes.maxOf { o -> o.time + o.duration }

                minHeight =
                    if (cursor.resizeFromTop) obj.height - notes.minOf { n -> obj.getY(n.midinote) }
                    else notes.maxOf { n -> obj.getY(n.midinote) + obj.pixelsPerPitch }
            }
            val deltaDur = dur.coerceAtLeast(minDur) - obj.duration
            val deltaHeight = height.coerceAtLeast(minHeight) - obj.height
            obj.duration += deltaDur
            val pitches = ((obj.height + deltaHeight) / pixelsPerPitchBeforeResize).roundToInt()
            if (pitches != obj.pitchRange.count()) {
                if (cursor.resizeFromTop) obj.highestPitch = obj.lowestPitch + pitches
                else obj.lowestPitch = obj.highestPitch - pitches
            }
            obj.height = pitches * pixelsPerPitchBeforeResize
            if (cursor.resizeFromLeft) {
                for (note in obj.notes()) {
                    note.time += deltaDur
                }
            }
        }
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
        val instrumentSelector = ObjectSelectorControl(obj.instrumentSelector, createBundle())
        header.children.add(instrumentSelector)
        addAction(Icon.Details, action = "Edit event dictionary") {
            showEventDictionaryEditor(obj.eventDictionary)
        }
        listenForMouseEvents()
    }

    private fun listenForMouseEvents() {
        cursor.viewOrder = 100.0
        cursor.fillProperty().bind(binding(backgroundColor, cursorOpacity) { background, opacity ->
            background.invert().deriveColor(0.0, 1.0, 1.0, opacity)
        }.asObservableValue())
        notePane.addEventHandler(MouseEvent.ANY) { ev ->
            if (context[XenakisUI].toolSelector.selected.value != ToolSelector.Tool.PianoRoll) {
                notePane.children.remove(cursor)
                return@addEventHandler
            }
            when (ev.eventType) {
                MouseEvent.MOUSE_ENTERED -> {
                    if (cursor !in notePane.children && !ev.isPrimaryButtonDown) notePane.children.add(cursor)
                }

                MouseEvent.MOUSE_MOVED -> {
                    cursor.x = snapToGrid(ev.x - 10.0, ev.y).x
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
        get() = obj.instrument.get().color

    companion object {
        private const val CURSOR_OPACITY = 0.6
    }
}