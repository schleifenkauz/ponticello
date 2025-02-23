package xenakis.ui.score

import bundles.createBundle
import hextant.fx.initHextantScene
import hextant.fx.registerShortcuts
import hextant.serial.EditorRoot
import javafx.beans.binding.Bindings
import javafx.geometry.HorizontalDirection
import javafx.geometry.VerticalDirection
import javafx.scene.Cursor
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Region
import javafx.scene.paint.Color
import javafx.scene.shape.Line
import javafx.scene.shape.Rectangle
import reaktive.value.ReactiveValue
import reaktive.value.binding.binding
import reaktive.value.binding.flatMap
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.*
import xenakis.model.Logger
import xenakis.model.obj.InstrumentObject
import xenakis.model.score.ObjectPosition
import xenakis.model.score.PianoRollObject
import xenakis.model.score.ScoreObject
import xenakis.model.score.ScoreObjectInstance
import xenakis.sc.editor.EventDictionaryEditor
import xenakis.sc.view.ObjectSelectorControl
import xenakis.ui.actions.ToolSelector
import xenakis.ui.controls.DetailPane
import xenakis.ui.impl.*
import xenakis.ui.launcher.XenakisMainScreen
import xenakis.ui.prompt.DecimalPrompt
import xenakis.ui.prompt.IntegerPrompt
import kotlin.math.roundToInt

class PianoRollObjectView(inst: ScoreObjectInstance, private val obj: PianoRollObject) : ScoreObjectView(inst) {
    private val noteRects = mutableMapOf<PianoRollObject.Note, BorderPane>()
    private val orientationLines = mutableListOf<Line>()
    private val blackKeys = mutableListOf<Rectangle>()
    private val pixelsPerPitch get() = prefHeight / (obj.highestPitch - obj.lowestPitch + 1)
    private val cursor = Rectangle(10.0, pixelsPerPitch) styleClass "note-cursor"
    private val cursorOpacity = reactiveVariable(CURSOR_OPACITY)
    private val selectedTool get() = context[XenakisMainScreen].toolSelector.selected

    private fun getY(pitch: Int) = (obj.highestPitch - pitch) * pixelsPerPitch

    private fun getMidiNote(y: Double): Int = ((height - y) / pixelsPerPitch).roundToInt() + obj.lowestPitch - 1

    fun addedNote(note: PianoRollObject.Note) {
        val rect = BorderPane() styleClass "note-object"
        rect.backgroundProperty().bind(Bindings.createObjectBinding({
            val background = backgroundColor.now
            if (rect.isFocused && selectedTool.value == ToolSelector.Tool.PianoRoll)
                background(background.interpolate(background.invert(), 0.8))
            else background(background.invert())
        }, backgroundColor.asObservableValue(), rect.focusedProperty(), selectedTool))
        rect.borderProperty().bind(Bindings.createObjectBinding({
            if (rect.isHover && selectedTool.value == ToolSelector.Tool.PianoRoll) {
                val background = backgroundColor.now.invert()
                solidBorder(background.darker(), 2.0)
            } else solidBorder(Color.TRANSPARENT, 2.0)
        }, backgroundColor.asObservableValue(), rect.hoverProperty(), selectedTool))
        noteRects[note] = rect
        setupNoteObjectEvents(rect, note)
        updateNoteDisplay(rect, note)
        children.add(rect)
    }

    private fun updateNoteDisplay(rect: Region, note: PianoRollObject.Note) {
        rect.layoutX = pane.getWidth(note.onset)
        rect.layoutY = getY(note.midinote)
        rect.prefWidth = pane.getWidth(note.duration)
        rect.prefHeight = pixelsPerPitch
    }

    private fun snapToGrid(x: Double, y: Double): Decimal {
        val pos = ObjectPosition(getTime(x), pane.getScoreY(y))
        return pane.context.rootPane.snapToGrid(pos + absolutePosition).time - absolutePosition.time
    }

    private fun setupNoteObjectEvents(rect: Region, note: PianoRollObject.Note) {
        rect.isFocusTraversable = true
        rect.setupDraggingAndResizing(
            context = pane.context,
            canUserChangeWidth = true, canUserChangeHeight = false,
            moveTool = ToolSelector.Tool.PianoRoll,
            resizeTool = ToolSelector.Tool.PianoRoll,
            drag = { toX, toY ->
                val t = snapToGrid(toX, toY)
                note.onset = t.coerceIn(zero, obj.duration - note.duration)
                note.midinote = getMidiNote(toY).coerceIn(obj.pitchRange)
            },
            resize = { old, dx, dy, cursor, _ ->
                when (cursor) {
                    Cursor.W_RESIZE -> {
                        val t = snapToGrid(old.minX + dx, old.minY + dy)
                        val oldTime = note.onset
                        note.onset = t.coerceAtLeast(zero)
                        note.duration += (oldTime - note.onset)
                    }

                    Cursor.E_RESIZE -> {
                        val t = snapToGrid(old.maxX + dx - old.minX, old.maxY + dy)
                        note.duration = t.coerceIn(zero, obj.duration - note.onset)
                    }
                }
            },
        )
        rect.addEventHandler(MouseEvent.ANY) { ev ->
            if (selectedTool.value != ToolSelector.Tool.PianoRoll) return@addEventHandler
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
        val window = SubWindow(control, "Note properties", context, type = SubWindow.Type.ToolWindow)
        window.scene.initHextantScene(context, applyStyle = false)
        window.resize(300.0, 200.0)
        window.show()
    }

    fun updatedNote(note: PianoRollObject.Note) {
        val rect = noteRects[note] ?: return Logger.error("Note $note was note displayed in $this")
        updateNoteDisplay(rect, note)
    }

    fun removedNote(note: PianoRollObject.Note) {
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
    }

    override fun initialize(parent: ScorePane) {
        super.initialize(parent)
        drawOrientationLines()
        shadeBlackKeys()
        listenForMouseEvents()
    }

    override fun setupDetailPane(pane: DetailPane) {
        val instrumentSelector = ObjectSelectorControl(obj.instrumentSelector, createBundle())
        pane.addItem("Instrument: ", instrumentSelector)
        pane.addItem("Color:", this.colorPicker)
        pane.addLargeItem("Event dictionary", this.obj.eventDictionary.control)
    }

    override fun adjustVertical(direction: VerticalDirection, resize: Boolean, resizeType: ScoreObject.ResizeType) {
        var deltaY = obj.height / (obj.highestPitch - obj.lowestPitch + 1)
        if (direction == VerticalDirection.UP) deltaY *= -1
        adjustVertical(resize, resizeType, deltaY)
    }

    fun showTransposeDialog() {
        val semitones = IntegerPrompt("Tranpose by semitones", 0, -36..36)
            .showDialog(context) ?: return
        obj.transpose(semitones)
    }

    private fun listenForMouseEvents() {
        cursor.viewOrder = 100.0
        cursor.fillProperty().bind(binding(backgroundColor, cursorOpacity) { background, opacity ->
            background.invert().deriveColor(0.0, 1.0, 1.0, opacity)
        }.asObservableValue())
        addEventHandler(MouseEvent.ANY) { ev ->
            val selectedTool = selectedTool.value
            if (selectedTool == ToolSelector.Tool.AddTime && ev.eventType == MouseEvent.MOUSE_CLICKED) {
                val t = snapToGrid(ev.x, ev.y)
                val amount = DecimalPrompt(
                    "How much time to add", precision = ObjectPosition.TIME_PRECISION,
                    10.0, 0.0..1000.0
                ).showDialog(context)
                    ?: return@addEventHandler
                obj.addTime(t, amount)
            }
            if (selectedTool != ToolSelector.Tool.PianoRoll) {
                children.remove(cursor)
                return@addEventHandler
            }
            when (ev.eventType) {
                MouseEvent.MOUSE_ENTERED -> {
                    if (cursor !in children && !ev.isPrimaryButtonDown) children.add(cursor)
                }

                MouseEvent.MOUSE_MOVED -> {
                    cursor.x = getX(snapToGrid(ev.x, ev.y))
                    cursor.y = ev.y.snap(pixelsPerPitch.toDecimal()).toDouble()
                    if (cursor.y > prefHeight) children.remove(cursor)
                }

                MouseEvent.MOUSE_EXITED -> {
                    children.remove(cursor)
                    ev.consume()
                }

                MouseEvent.MOUSE_PRESSED -> {
                    cursorOpacity.now = 1.0
                    ev.consume()
                }

                MouseEvent.MOUSE_DRAGGED -> {
                    cursor.width = getWidth(snapToGrid(ev.x - cursor.x, ev.y))
                    ev.consume()
                }

                MouseEvent.MOUSE_RELEASED -> {
                    val time = pane.getDuration(cursor.x)
                    val duration = pane.getDuration(cursor.width)
                    val midinote = getMidiNote(cursor.y)
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
        cursor.height = pixelsPerPitch
    }

    override val defaultBackgroundColor: ReactiveValue<Color>
        get() = obj.instrumentSelector.selected.flatMap { instr -> instr.get<InstrumentObject>().color }

    companion object {
        private const val CURSOR_OPACITY = 0.6
    }
}