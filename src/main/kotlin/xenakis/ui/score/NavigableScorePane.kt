package xenakis.ui.score

import fxutils.Ctrl
import fxutils.SubWindow
import fxutils.modifiers
import fxutils.styleClass
import hextant.context.Context
import javafx.application.Platform
import javafx.geometry.Point2D
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.shape.Line
import reaktive.event.unitEvent
import reaktive.value.now
import xenakis.impl.*
import xenakis.model.flow.AudioFlowGroup
import xenakis.model.flow.AudioFlows
import xenakis.model.player.PlaybackManager
import xenakis.model.project.UIState.SnapOption
import xenakis.model.project.settings
import xenakis.model.score.*
import xenakis.ui.controls.NamePrompt
import xenakis.ui.impl.verticalDist
import xenakis.ui.launcher.XenakisLauncher.Companion.currentProject
import xenakis.ui.launcher.XenakisMainActivity
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread
import kotlin.math.exp

class NavigableScorePane(score: Score, context: Context) : ScorePane(score, context) {
    private val positionTracker = Line() styleClass "mouse-tracker-line"

    private var latestRepaintTrigger = 0L
    private val repaint = unitEvent()
    val onRepaint get() = repaint.stream

    override var displayStart: Decimal = 0.0.asTime

    override var displayEnd: Decimal = 0.0.asTime

    override val pixelsPerSecond: Double
        get() = (this.width / (displayEnd - displayStart)).toDouble()

    override val associatedObject: ScoreObjectGroup?
        get() = null

    override val absolutePosition: ObjectPosition
        get() = ObjectPosition.ZERO

    override val root: ScorePane
        get() = this

    val displayedDuration get() = displayEnd - displayStart

    private var magnifiedEnvelope: EnvelopeEditor? = null
    private var magnifierWindow: SubWindow? = null

    init {
        styleClass.add("score-view")
        isFocusTraversable = true
        listenForEvents()
        heightProperty().addListener { _ -> repaint() }
        widthProperty().addListener { _ -> repaint() }
    }

    fun initialize() {
        this.score.addListener(this)
    }

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
        for (g in grids) {
            if (t in g.instance.timeRange) g.mark(t - g.instance.start)
            else g.unmark()
        }
        positionTracker.layoutX = getX(t)
        val activity = context[XenakisMainActivity]
        val playbackManager = context[PlaybackManager]
        if (this == activity.scoreView && !(playbackManager.isAttachedTo(this) && playbackManager.isPlaying.now)) {
            activity.timeCodeView.displayTime(t)
        }
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
        magnifiedEnvelope = EnvelopeEditor(editor.namedControl, editor.envelope, editor.objectView, pane)
        val objName = editor.objectView.instance.obj.name.now
        val title = "Envelope for ${editor.parameterName} of $objName"
        magnifierWindow?.hide()
        magnifierWindow = SubWindow(pane, title, SubWindow.Type.Popup)
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
        noNegativeTimes()
        repaint()
    }

    private fun noNegativeTimes() {
        if (displayStart < zero) {
            displayEnd -= displayStart
            displayStart -= displayStart
        }
    }

    override fun repaint() {
        latestRepaintTrigger = System.currentTimeMillis()
        layoutObjects()
        repositionEnvelopeMagnifier()
        repaint.fire()
        if (positionTracker !in children) children.add(positionTracker)
        activity.playback.playHead.updatePosition()
    }

    private fun layoutObjects() {
        val repaintTrigger = latestRepaintTrigger
        val maxTime = 25L //determines how much time is spent consecutively on the Application Thread
        val itr = views.iterator()
        thread {
            while (itr.hasNext()) {
                if (repaintTrigger != latestRepaintTrigger) {
                    break
                }
                val job = CompletableFuture<Unit>()
                Platform.runLater { layoutObjects(itr, maxTime, job) }
                job.join()
            }
        }
    }

    override fun listenForEvents() {
        super.listenForEvents()
        isFocusTraversable = true
        setupPositionTracker()
        setupNavigation()
    }

    override fun rightClicked(ev: MouseEvent) {
        super.rightClicked(ev)
        if (ev.modifiers == setOf(Ctrl)) {
            addFlowGroup(ev)
        }
    }

    private fun addFlowGroup(ev: MouseEvent) {
        val y = getScoreY(ev.y)
        val name = NamePrompt(context[AudioFlows], "Name for new flow group", "")
            .showDialog(scene.window, Point2D(ev.x, ev.y)) ?: return
        val color = randomColor()
        val group = AudioFlowGroup.create(name, y, color)
        context[AudioFlows].add(group)
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
            context[ScoreObjectDuplicator].movedCursor(this, t, y)
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

    fun zoom(amount: Double, evX: Double) {
        val newIntervalSize = (displayEnd - displayStart) * amount
        val oldIntervalCenter = (displayEnd + displayStart) / 2
        val newIntervalCenter = (getTime(evX) + oldIntervalCenter * 3) / 4
        display(newIntervalCenter - (newIntervalSize / 2), newIntervalCenter + (newIntervalSize / 2))
    }

    fun displaySelectedArea() {
        val area = selectedArea ?: return
        clearRegionSelection()
        display(area.time, area.time + area.duration)
    }

    fun scroll(amount: Double) {
        displayStart += amount
        displayEnd += amount
        display(displayStart + amount, displayEnd + amount)
        repaint()
    }
}