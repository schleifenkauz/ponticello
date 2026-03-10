package ponticello.ui.score

import fxutils.Alt
import fxutils.modifiers
import fxutils.styleClass
import hextant.context.Context
import hextant.fx.ModifierKeyTracker
import javafx.scene.Cursor
import javafx.scene.input.MouseEvent
import javafx.scene.paint.Color
import javafx.scene.shape.Line
import javafx.scene.shape.Rectangle
import ponticello.impl.*
import ponticello.model.obj.project
import ponticello.model.project.uiState
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.score.*
import ponticello.model.score.controls.ParameterControlList
import ponticello.model.score.controls.ValueControl
import reaktive.value.binding.`if`
import reaktive.value.binding.or
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import reaktive.value.reactiveVariable
import java.util.concurrent.Future
import kotlin.math.roundToInt

class MidiScorePane(
    private val instance: ScoreObjectInstance,
    private val obj: MidiObject,
    override val associatedView: MidiObjectView,
    context: Context,
) : ScorePane(obj.score, context) {
    private val orientationLines = mutableListOf<Line>()
    private val keys = mutableListOf<Rectangle>()
    val pixelsPerPitch get() = prefHeight / (obj.highestPitch - obj.lowestPitch + 1)
    private val cursorNode = Rectangle()
    private val isCreatingNode = reactiveVariable(false)

    val parentPane get() = associatedView.parentPane

    override val root: ScorePane
        get() = parentPane.root
    override val displayStart: Decimal
        get() = 0.0.asTime
    override val displayEnd: Decimal
        get() = obj.duration

    override val yRange: DecimalRange
        get() = zero..obj.highestPitch.toDecimal()

    override val pixelsPerSecond: Double
        get() = parentPane.pixelsPerSecond

    override val absolutePosition: ObjectPosition
        get() = parentPane.absolutePosition + instance.position

    override val associatedObject get() = obj

    fun initialize() {
        listenForEvents()
        obj.score.addListener(this)
        setupCursor()
        drawOrientationLines()
        shadeKeys()
        cursorProperty().bind(
            `if`(
                ModifierKeyTracker.isAltDown or isCreatingNode,
                then = { Cursor.NONE },
                otherwise = { null }).asObservableValue()
        )
    }

    private fun setupCursor() {
        cursorNode.viewOrder = -1000.0
        cursorNode.visibleProperty().bind(
            (ModifierKeyTracker.isAltDown or isCreatingNode).asObservableValue()
        )
        cursorNode.opacity = CURSOR_OPACITY
        children.add(cursorNode)
    }

    override fun acceptObject(obj: ScoreObject): ScoreObject? = when (obj) {
        is SoundProcess -> obj
        else -> null
    }

    private fun getMidiNote(y: Double): Int = ((height - y) / pixelsPerPitch).roundToInt() + obj.lowestPitch - 1

    private fun drawOrientationLines() {
        children.removeAll(orientationLines)
        orientationLines.clear()
        for (pitch in obj.lowestPitch until obj.highestPitch) {
            val line = Line() styleClass "pitch-line"
            val y = ((obj.highestPitch - pitch.toDecimal()) * pixelsPerPitch).toDouble()
            line.startY = y
            line.endY = y
            line.endX = prefWidth
            line.isMouseTransparent = true
            orientationLines.add(line)
        }
        children.addAll(orientationLines)
    }

    private fun shadeKeys() {
        children.removeAll(keys)
        keys.clear()
        for (pitch in obj.pitchRange) {
            val y = ((obj.highestPitch - pitch.toDecimal()) * pixelsPerPitch).toDouble()
            val shade = Rectangle(0.0, y, prefWidth, pixelsPerPitch)
            shade.viewOrder = 100.0
            shade.isMouseTransparent = true
            shade.fill =
                if (MidiPitch(pitch).isBlackKey()) Color.gray(0.3)
                else Color.gray(0.7)
            keys.add(shade)
        }
        children.addAll(keys)
    }

    override fun repaint(): Future<*> {
        drawOrientationLines()
        shadeKeys()
        return super.repaint()
    }

    override fun getScreenY(scoreY: Decimal): Double = ((obj.highestPitch - scoreY) * pixelsPerPitch).toDouble()

    override fun getScoreY(screenY: Double): Decimal = obj.highestPitch - (screenY / pixelsPerPitch).toDecimal()

    override fun snapToGrid(x: Double, y: Double): ObjectPosition {
        val scoreY = y * (obj.height / this.prefHeight)
        val scoreTime = getDuration(x)
        val time = super.snapToGrid(ObjectPosition(scoreTime, scoreY)).time
        val pitchY = getScoreY(y).round(precision = 0)
        return ObjectPosition(time, pitchY)
    }

    override fun snapToGrid(position: ObjectPosition): ObjectPosition {
        val screenY = getScreenY(position.y)
        val scoreY = screenY * (obj.height / this.prefHeight)
        val time = super.snapToGrid(ObjectPosition(position.time, scoreY)).time
        val y = position.y.round(precision = 0)
        return ObjectPosition(time, y)
    }

    override fun coerceAndTransformScoreY(y: Decimal, obj: ScoreObject): Decimal =
        super.coerceAndTransformScoreY(y, obj).round(precision = 0)

    override fun mouseExited() {
        children.remove(cursorNode)
        isCreatingNode.now = false
    }

    override fun mouseMoved(ev: MouseEvent) {
        if (!boundsInParent.contains(ev.x, ev.y)) {
            mouseExited()
            return
        }
        if (cursorNode !in children) {
            children.add(cursorNode)
        }
        cursorNode.x = getX(snapToGrid(ev.x, ev.y).time)
        cursorNode.y = ev.y.snap(pixelsPerPitch.toDecimal()).toDouble()
        cursorNode.width = 5.0
        cursorNode.fill = associatedObject.associatedColor.now ?: Color.BLACK
        cursorNode.height = pixelsPerPitch
    }

    override fun mouseDragDetected(ev: MouseEvent) {
        if (ev.modifiers == setOf(Alt)) {
            cursorNode.opacity = 1.0
            isCreatingNode.now = true
            ev.consume()
        } else {
            super.mouseDragDetected(ev)
        }
    }

    override fun mouseDragged(ev: MouseEvent) {
        if (isCreatingNode.now) {
            val noteEnd = snapToGrid(ev.x, ev.y).time
            val noteStart = getTime(cursorNode.x)
            cursorNode.width = getWidth(noteEnd - noteStart)
            parentPane.markT(instance.start + noteEnd)
            ev.consume()
        } else {
            super.mouseDragged(ev)
        }
    }

    override fun mouseReleased(ev: MouseEvent) {
        if (isCreatingNode.now) {
            val time = getDuration(cursorNode.x)
            val duration =
                if (lastMousePress != null && lastMousePress!!.distance(ev.x, ev.y) > 0.1) {
                    getDuration(cursorNode.width)
                } else {
                    val grid = parentPane.getNearestGrid(instance.position)
                    val settings = context.project.uiState
                    val snapOption =
                        if (settings.snapEnabled.now) settings.snapOption.now
                        else null
                    snapOption?.let { grid?.meter?.getDuration(it) } ?: getDuration(cursorNode.width)
                }
            if (duration <= zero) return
            val midinote = getMidiNote(cursorNode.y)
            val name = context[ScoreObjectRegistry].availableName("midinote")
            val controls = ParameterControlList.create(
                "velocity" to ValueControl.create(64.toDecimal()),
                "channel" to ValueControl.create(0.toDecimal())
            )
            val note = SoundProcess.create(name, obj.instrument.now, controls)
            note.setInitialSize(duration, height = zero)
            val inst = ScoreObjectInstance(note, time, y = midinote.toDecimal())
            obj.score.addObject(inst, autoSelect = true)
            cursorNode.opacity = CURSOR_OPACITY
            isCreatingNode.now = false
            ev.consume()
        } else {
            super.mouseReleased(ev)
        }
    }

    companion object {
        private const val CURSOR_OPACITY = 0.6
    }
}