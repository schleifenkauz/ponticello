package xenakis.ui

import hextant.context.Context
import hextant.fx.registerShortcuts
import javafx.geometry.Rectangle2D
import javafx.scene.Node
import javafx.scene.SnapshotParameters
import javafx.scene.image.ImageView
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.robot.Robot
import javafx.scene.shape.Line
import javafx.scene.text.Text
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import xenakis.impl.Point
import xenakis.impl.replacePrefix
import xenakis.impl.step
import xenakis.model.InteractionSettings.SnapOption
import xenakis.model.Logger
import xenakis.model.ObjectPosition
import xenakis.model.Score
import xenakis.model.ScoreObject
import xenakis.ui.XenakisController.Companion.currentProject
import kotlin.math.exp
import kotlin.math.roundToInt

class ScoreView(score: Score, context: Context) : ScorePane(score, context) {
    private val positionTracker = Line() styleClass "mouse-tracker-line"
    private val robot = Robot()

    var clipboardObject: ScoreObject? = null
        private set
    var clipboardMode: ClipboardMode? = null
        private set
    private val clipboardObjectView: ImageView = ImageView().also { v -> v.isVisible = false }

    private var timeGrid: Double = 0.1
    override val xAccuracy: Int
        get() = accuracy(timeGrid)
    public override var displayStart: Double = 0.0
    override var displayEnd: Double = 0.0
    override val pixelsPerSecond: Double
        get() = width / (displayEnd - displayStart)
    override val rootPane: ScoreView
        get() = this
    val displayedDuration get() = displayEnd - displayStart

    private var magnifiedEnvelope: EnvelopeEditor? = null
    private var magnifierWindow: SubWindow? = null

    init {
        children.add(clipboardObjectView)
        isFocusTraversable = true
        listenForEvents()
        score.addListener(this)
        styleClass.add("score-view")
        selectedArea.registerShortcuts {
            on("S") {
                displayStart = getTime(selectedArea.x)
                displayEnd = getTime(selectedArea.x + selectedArea.width)
                repaint()
            }
        }
        heightProperty().addListener { _ -> repaint() }
        widthProperty().addListener { _ -> repaint() }
    }

    enum class ClipboardMode {
        Duplicate, Clone;
    }

    fun setClipboard(obj: ScoreObject, view: ScoreObjectView, mode: ClipboardMode) {
        clipboardObject = obj
        clipboardMode = mode
        val parameters = SnapshotParameters()
        view.snapshot(parameters, null)
        clipboardObjectView.image = view.snapshot(parameters, null)
        clipboardObjectView.viewport = Rectangle2D(5.0, 3.0, view.prefWidth - 4.0, view.prefHeight - 4.0)
        val mousePos = screenToLocal(robot.mousePosition)
        val (x, y) = snapToGrid(mousePos.x, mousePos.y)
        clipboardObjectView.relocate(x, y)
        clipboardObjectView.opacity = 0.3
        clipboardObjectView.viewOrder = -1000.0
        clipboardObjectView.isMouseTransparent = true
        clipboardObjectView.visibleProperty().bind(hoverProperty())
    }

    fun clearClipboard() {
        clipboardObject = null
        clipboardMode = null
        clipboardObjectView.visibleProperty().unbind()
        clipboardObjectView.isVisible = false
    }

    fun isInDuplicateMode() = clipboardObject != null

    override fun getGrids(x: Double): List<TempoGridObjectView> = allViews.filterIsInstance<TempoGridObjectView>()
        .filter { v -> x in v.layoutX..v.width }

    override fun markX(x: Double) {
        super.markX(x)
        positionTracker.layoutX = x
    }

    fun translateFrom(child: Node, x: Double, y: Double): Point {
        if (child == this) return Point(x, y)
        var coords = child.localToScreen(x, y)
        coords = screenToLocal(coords)
        return Point(coords)
    }

    fun translateFrom(child: Node, position: ObjectPosition): ObjectPosition {
        if (child == this) return position
        val (x, y) = translateFrom(child, getWidth(position.time), getPaneY(position.y))
        return ObjectPosition(getTime(x), getScoreY(y))
    }

    fun translateTimeFrom(child: Node, time: Double): Double = translateFrom(child, ObjectPosition(time, 0.0)).time

    fun translateTo(child: Node, x: Double, y: Double): Point {
        if (child == this) return Point(x, y)
        var coords = localToScreen(x, y)
        coords = child.screenToLocal(coords)
        return Point(coords)
    }

    fun translateTo(child: Node, position: ObjectPosition): ObjectPosition {
        if (child == this) return position
        val (x, y) = translateTo(child, getWidth(position.time), getPaneY(position.y))
        return ObjectPosition(getWidth(x), getScoreY(y))
    }

    fun translateTimeTo(child: Node, time: Double): Double = translateTo(child, ObjectPosition(time, 0.0)).time

    override fun snapToGrid(x: Double, y: Double): Point {
        val settings = context[currentProject].settings
        if (!settings.snapEnabled.now) return Point(x, y)
        when (val option = settings.snapOption.now) {
            SnapOption.Seconds -> return Point(getX(getTime(x).roundToInt().toDouble()), y)
            else -> {
                val nearestGrid = getNearestGrid(x, y)
                for (grid in allViews.filterIsInstance<TempoGridObjectView>()) {
                    if (grid != nearestGrid) grid.unmark()
                }
                if (nearestGrid == null) return Point(x, y)
                var t = getTime(x)
                t = nearestGrid.obj.snapToGrid(nearestGrid.instance, t, option)
                val snappedX = getX(t)
                return Point(snappedX, y)
            }
        }
    }

    override fun getNearestGrid(x: Double, y: Double): TempoGridObjectView? {
        val grids = allViews.filterIsInstance<TempoGridObjectView>()
        val relevantGrids = grids.filter { g -> x in g.layoutX..g.width }
        val nearestGrid = relevantGrids.minByOrNull { g -> g.verticalDist(y) }
        return nearestGrid
    }

    fun magnifyEnvelope(editor: EnvelopeEditor) {
        val pane = Pane() styleClass "envelope-sub-window"
        val semitransparent = editor.objectView.backgroundColor.now.deriveColor(1.0, 1.0, 1.0, 0.3)
        pane.style = "-fx-background-color: ${semitransparent.toString().replacePrefix("0x", "#")};"
        magnifiedEnvelope = EnvelopeEditor(editor.parameterName, editor.envelope, editor.objectView, pane)
        val objName = editor.objectView.instance.obj.name.now
        val title = "Envelope for ${editor.parameterName} of $objName"
        magnifierWindow?.hide()
        magnifierWindow = SubWindow(pane, title, editor.objectView.context, SubWindow.Type.Popup)
        repositionEnvelopeMagnifier()
        magnifierWindow!!.show()
    }

    private fun repositionEnvelopeMagnifier() {
        val editor = magnifiedEnvelope ?: return
        val window = magnifierWindow ?: return
        editor.pane.setPrefSize(editor.objectView.prefWidth, height / 5)
        editor.repaint()
        val coords = editor.objectView.localToScreen(0.0, 0.0) ?: return
        window.sizeToScene()
        window.x = coords.x
        window.y =
            if (coords.y + editor.objectView.prefHeight / 2 > height / 2) coords.y - editor.pane.prefHeight - 10.0
            else coords.y + editor.objectView.prefHeight + 10.0
        window.scene.fill = Color.TRANSPARENT
    }

    fun displayWholeScore() {
        val totalDuration = score.objectInstances.maxOfOrNull { obj -> obj.start + obj.duration } ?: 60.0
        display(0.0, totalDuration)
    }

    fun display(start: Double, end: Double) {
        if (end < start) {
            Logger.severe("Attempt to display empty time range: $start .. $end", Logger.Category.Score)
            return
        }
        displayStart = start
        displayEnd = end
        repaint()
    }

    override fun repaint() {
        super.repaint()
        repositionEnvelopeMagnifier()
        children.add(clipboardObjectView)
        children.add(positionTracker)
        ui.playback.playHead.updatePosition()
        displayTimeGrid()
    }

    private fun displayTimeGrid() {
        val settings = context[currentProject].settings
        val gridVisible = settings.displayTimeGrid.asObservableValue()
        var idx = QUANTIZED_PIXELS_PER_SECOND.binarySearchBy(pixelsPerSecond) { s -> s }
        if (idx < 0) idx = (-(idx + 1)).coerceAtMost(QUANTIZED_PIXELS_PER_SECOND.size - 1)
        val quantizedPixelsPerSecond = QUANTIZED_PIXELS_PER_SECOND[idx]
        timeGrid = (1 / quantizedPixelsPerSecond) * 50.0
        for (t in displayStart..displayEnd step timeGrid) {
            val x = getX(t)
            val l = Line() styleClass "grid-line"
            l.viewOrder = -100.0
            l.startX = x
            l.endX = x
            l.startYProperty().bind(heightProperty().subtract(40))
            l.endYProperty().bind(heightProperty().subtract(5))
            l.visibleProperty().bind(gridVisible)
            children.add(l)
            val timeCode = timeCode(t, xAccuracy)
            val txt = Text(timeCode).styleClass("grid-time-code")
            txt.x = x - 8
            txt.yProperty().bind(heightProperty().subtract(40))
            txt.visibleProperty().bind(gridVisible)
            children.add(txt)
        }
    }

    override fun listenForEvents() {
        super.listenForEvents()
        setupPositionTracker()
        setupNavigation()
    }

    private fun setupPositionTracker() {
        positionTracker.startY = 5.0
        positionTracker.endYProperty().bind(heightProperty().subtract(5))
        positionTracker.viewOrder = -100.0
        positionTracker.isMouseTransparent = true
        positionTracker.visibleProperty().bind(hoverProperty())
        setOnMouseMoved { ev ->
            if (!ev.x.isNaN()) {
                val (x, y) = snapToGrid(ev.x, ev.y)
                markX(x)
                clipboardObjectView.relocate(x, y)
                ev.consume()
            }
        }
    }

    private fun setupNavigation() {
        setOnScroll { ev ->
            if (ev.isControlDown) {
                val factor = exp(-ev.deltaY * 0.01)
                zoom(factor, ev.x)
            } else {
                scroll(-ev.deltaX / pixelsPerSecond)
            }
        }
    }

    private fun zoom(amount: Double, evX: Double) {
        val newIntervalSize = (displayEnd - displayStart) * amount
        val oldIntervalCenter = (displayEnd + displayStart) / 2
        val newIntervalCenter = (getTime(evX) + oldIntervalCenter * 3) / 4
        displayStart = newIntervalCenter - (newIntervalSize / 2)
        displayEnd = newIntervalCenter + (newIntervalSize / 2)
        noNegativeTimes()
        repaint()
    }

    fun scroll(amount: Double) {
        displayStart += amount
        displayEnd += amount
        noNegativeTimes()
        repaint()
    }

    private fun noNegativeTimes() {
        if (displayStart < 0) {
            displayEnd -= displayStart
            displayStart -= displayStart
        }
    }

    companion object {
        private val QUANTIZED_PIXELS_PER_SECOND = listOf(1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0)
    }
}