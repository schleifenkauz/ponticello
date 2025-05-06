package xenakis.ui.score

import fxutils.SubWindow
import fxutils.styleClass
import hextant.context.Context
import javafx.application.Platform
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.shape.Line
import reaktive.event.unitEvent
import reaktive.value.now
import xenakis.impl.Decimal
import xenakis.impl.div
import xenakis.impl.replacePrefix
import xenakis.impl.round
import xenakis.model.obj.MeterObject
import xenakis.model.player.ScorePlayer
import xenakis.model.project.settings
import xenakis.model.score.*
import xenakis.ui.impl.verticalDist
import xenakis.ui.launcher.XenakisLauncher.Companion.currentProject
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread

abstract class RootScorePane(score: Score, context: Context) : ScorePane(score, context) {
    private val positionTracker = Line() styleClass "mouse-tracker-line"

    private var latestRepaintTrigger = 0L
    private val repaint = unitEvent()
    val onRepaint get() = repaint.stream

    private var magnifiedEnvelope: EnvelopeEditor? = null
    private var magnifierWindow: SubWindow? = null

    final override val root: ScorePane
        get() = this
    final override val associatedObject: ScoreObjectGroup?
        get() = null
    final override val pixelsPerSecond: Double
        get() = (this.width / (displayEnd - displayStart)).toDouble()
    override val absolutePosition: ObjectPosition
        get() = ObjectPosition.ZERO

    open fun initialize() {
        listenForEvents()
        this.score.addListener(this)
    }

    override fun listenForEvents() {
        super.listenForEvents()
        isFocusTraversable = true
        setupPositionTracker()
    }

    private fun setupPositionTracker() {
        positionTracker.startY = 5.0
        positionTracker.endYProperty().bind(heightProperty().subtract(5))
        positionTracker.viewOrder = -100.0
        positionTracker.isMouseTransparent = true
        positionTracker.visibleProperty().bind(hoverProperty())
        setOnMouseMoved { ev -> mouseMoved(ev) }
        setOnMouseExited { mouseExited() }
    }

    protected open fun mouseMoved(ev: MouseEvent) {
        val (t, y) = snapToGrid(ev.x, ev.y)
        markT(t)
        context[ScoreObjectDuplicator].movedCursor(this, t, y)
        ev.consume()
    }

    protected open fun mouseExited() {
        for (gridView in allViews.filterIsInstance<TempoGridObjectView>()) {
            gridView.unmark()
        }
        val player = context[ScorePlayer.CURRENT]
        context[TimeCodeView].displayTime(player.currentTime)
    }

    fun magnifyEnvelope(editor: EnvelopeEditor) {
        val pane = Pane() styleClass "envelope-sub-window"
        val semitransparent = editor.objectView.backgroundColor.now.deriveColor(1.0, 1.0, 1.0, 0.3)
        pane.style = "-fx-background-color: ${semitransparent.toString().replacePrefix("0x", "#")};"
        magnifiedEnvelope = EnvelopeEditor(editor.namedControl, editor.envelope, editor.objectView, pane)
        val objName = editor.objectView.obj.name.now
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

    override fun repaint() {
        latestRepaintTrigger = System.currentTimeMillis()
        layoutObjects()
        repositionEnvelopeMagnifier()
        repaint.fire()
        if (positionTracker !in children) children.add(positionTracker)
        context[ScorePlayer.CURRENT].playHead.updatePosition()
    }

    override fun getNearestGrid(position: ObjectPosition): Pair<Decimal, MeterObject>? {
        val grids = score.objectInstances.filter { inst ->
            val obj = inst.obj
            obj is TempoGridObject && obj.meter.isResolved.now
        }
        val relevantGrids = grids.filter { g -> position.time in g.timeRange }
        val nearestGrid = relevantGrids.minByOrNull { g -> g.verticalDist(position.y) } ?: return null
        val gridObj = nearestGrid.obj as TempoGridObject
        return nearestGrid.start to gridObj.meter.force()
    }

    final override fun snapToGrid(position: ObjectPosition): ObjectPosition {
        val settings = context[currentProject].settings
        val (t, y) = position
        if (!settings.snapEnabled.now) return position
        when (val option = settings.snapOption.now) {
            TimeUnit.Seconds -> return ObjectPosition(t.round(0), y)
            else -> {
                val nearestGrid = getNearestGrid(position)
                val gridStart = nearestGrid?.first
                val meter = nearestGrid?.second
                for (grid in allViews.filterIsInstance<TempoGridObjectView>()) {
                    if (grid.instance.start != gridStart) grid.unmark()
                }
                if (meter == null || gridStart == null) return position
                val snapped = meter.snapToGrid(t - gridStart, option)
                return ObjectPosition(snapped + gridStart, y)
            }
        }
    }

    override fun markT(t: Decimal) {
        val grids = allViews.filterIsInstance<TempoGridObjectView>()
        for (g in grids) {
            if (t in g.instance.timeRange) g.mark(t - g.instance.start)
            else g.unmark()
        }
        positionTracker.layoutX = getX(t)
        val player = context[ScorePlayer.CURRENT]
        if (!player.isPlaying.now) {
            context[TimeCodeView].displayTime(t)
        }
    }
}