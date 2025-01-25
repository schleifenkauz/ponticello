package xenakis.ui.score

import hextant.context.Context
import hextant.fx.registerShortcuts
import javafx.geometry.Rectangle2D
import javafx.scene.Node
import javafx.scene.SnapshotParameters
import javafx.scene.image.ImageView
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.robot.Robot
import javafx.scene.shape.Line
import javafx.scene.text.Text
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import xenakis.impl.*
import xenakis.model.InteractionSettings.SnapOption
import xenakis.model.Logger
import xenakis.model.score.*
import xenakis.ui.XenakisController.Companion.currentProject
import xenakis.ui.impl.SubWindow
import xenakis.ui.impl.styleClass
import xenakis.ui.impl.verticalDist
import kotlin.math.exp

class ScoreView(score: Score, context: Context) : ScorePane(score, context) {
    private val positionTracker = Line() styleClass "mouse-tracker-line"
    private val robot = Robot()

    var clipboardObject: ScoreObject? = null
        private set
    private val clipboardObjectView: ImageView = ImageView().also { v -> v.isVisible = false }
    private val timeGridObjects = mutableListOf<Node>()

    private var timeGrid: Double = 0.1
    val xAccuracy: Int
        get() = accuracy(timeGrid)
    override var displayStart: Decimal = 0.0.asTime

    override var displayEnd: Decimal = 0.0.asTime

    val pixelsPerSecond: Double
        get() = (width / (displayEnd - displayStart)).toDouble()

    override val associatedObject: ScoreObjectGroup?
        get() = null

    override val absolutePosition: ObjectPosition
        get() = ObjectPosition.ZERO

    val displayedDuration get() = displayEnd - displayStart

    private var magnifiedEnvelope: EnvelopeEditor? = null
    private var magnifierWindow: SubWindow? = null

    init {
        styleClass.add("score-view")
        children.add(clipboardObjectView)
        isFocusTraversable = true
        listenForEvents()
        heightProperty().addListener { _ -> repaint() }
        widthProperty().addListener { _ -> repaint() }
    }

    fun initialize() {
        this.score.addListener(this)
    }

    override fun startSelection(pos: ObjectPosition, ev: MouseEvent) {
        super.startSelection(pos, ev)
        val selection = selectedArea!!
        selection.rect.registerShortcuts {
            on("S") {
                displayStart = selection.time.coerceAtMost(0.0.asTime)
                displayEnd = selection.time + selection.duration
                repaint()
            }
        }
    }

    fun setClipboard(obj: ScoreObject, view: ScoreObjectView) {
        clipboardObject = obj
        val parameters = SnapshotParameters()
        view.snapshot(parameters, null)
        clipboardObjectView.image = view.snapshot(parameters, null)
        clipboardObjectView.viewport = Rectangle2D(5.0, 3.0, view.prefWidth - 4.0, view.prefHeight - 4.0)
        val mousePos = screenToLocal(robot.mousePosition)
        val (t, y) = snapToGrid(mousePos.x, mousePos.y)
        clipboardObjectView.relocate(getX(t), getPaneY(y))
        clipboardObjectView.opacity = 0.3
        clipboardObjectView.viewOrder = -1000.0
        clipboardObjectView.isMouseTransparent = true
        clipboardObjectView.visibleProperty().bind(hoverProperty())
    }

    fun clearClipboard() {
        clipboardObject = null
        clipboardObjectView.visibleProperty().unbind()
        clipboardObjectView.isVisible = false
    }

    fun isInDuplicateMode() = clipboardObject != null

    override fun snapToGrid(position: ObjectPosition): ObjectPosition {
        val settings = context[currentProject].settings
        val (t, y) = position
        if (!settings.snapEnabled.now) return position
        when (val option = settings.snapOption.now) {
            SnapOption.Seconds -> return ObjectPosition(t.round(0), y)
            else -> {
                val nearestGrid = getNearestGrid(position)
                for (grid in allViews.filterIsInstance<TempoGridObjectView>()) {
                    if (grid.instance != nearestGrid) grid.unmark()
                }
                if (nearestGrid == null) return position
                val obj = nearestGrid.obj as TempoGridObject
                val snapped = obj.snapToGrid(t - nearestGrid.start, option)
                return ObjectPosition(snapped + nearestGrid.start, y)
            }
        }
    }

    override fun markT(t: Decimal) {
        val grids = allViews
            .filterIsInstance<TempoGridObjectView>()
            .filter { v -> t in v.instance.timeRange }
        for (g in grids) {
            g.mark(t - g.instance.start)
        }
        positionTracker.layoutX = getX(t)
    }

    override fun getNearestGrid(position: ObjectPosition): ScoreObjectInstance? {
        val grids = score.objectInstances.filter { inst -> inst.obj is TempoGridObject }
        val relevantGrids = grids.filter { g -> position.time in g.timeRange }
        val nearestGrid = relevantGrids.minByOrNull { g -> g.verticalDist(position.y) }
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
        val totalDuration = score.objectInstances.maxOfOrNull { obj -> obj.start + obj.duration } ?: 60.0.asTime
        display(zero, totalDuration)
    }

    fun display(start: Decimal, end: Decimal) {
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
        if (clipboardObjectView !in children) children.add(clipboardObjectView)
        if (positionTracker !in children) children.add(positionTracker)
        ui.playback.playHead.updatePosition()
        displayTimeGrid()
    }

    private fun displayTimeGrid() {
        children.removeAll(timeGridObjects)
        timeGridObjects.clear()
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
            timeGridObjects.add(l)
            val timeCode = timeCode(t, xAccuracy)
            val txt = Text(timeCode).styleClass("grid-time-code")
            txt.x = x - 8
            txt.yProperty().bind(heightProperty().subtract(40))
            txt.visibleProperty().bind(gridVisible)
            timeGridObjects.add(txt)
        }
        children.addAll(timeGridObjects)
    }

    override fun listenForEvents() {
        super.listenForEvents()
        isFocusTraversable = true
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
            val (t, y) = snapToGrid(ev.x, ev.y)
            markT(t)
            clipboardObjectView.relocate(getX(t), getPaneY(y))
            ev.consume()
        }
    }

    private fun setupNavigation() {
        setOnScroll { ev ->
            if (ev.isControlDown) {
                val factor = exp(-ev.deltaY * 0.01)
                zoom(factor, ev.x)
            } else {
                scroll(-ev.deltaY / pixelsPerSecond)
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
        if (displayStart < zero) {
            displayEnd -= displayStart
            displayStart -= displayStart
        }
    }

    companion object {
        private val QUANTIZED_PIXELS_PER_SECOND = listOf(1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0)
    }
}