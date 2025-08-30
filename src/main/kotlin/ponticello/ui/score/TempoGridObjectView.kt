package ponticello.ui.score

import fxutils.controls.IntSpinner
import fxutils.hspace
import fxutils.prompt.DetailPane
import fxutils.styleClass
import fxutils.undo.UndoManager
import hextant.context.Context
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.scene.canvas.Canvas
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.Background
import javafx.scene.paint.Color
import javafx.scene.shape.Line
import javafx.scene.text.Font
import ponticello.impl.Decimal
import ponticello.impl.zero
import ponticello.model.obj.MeterObject
import ponticello.model.obj.project
import ponticello.model.project.InlineControlsDisplay
import ponticello.model.project.UIState
import ponticello.model.project.uiState
import ponticello.model.score.ScoreObjectInstance
import ponticello.model.score.TempoGridObject
import ponticello.model.score.TimeUnit
import reaktive.Observer
import reaktive.and
import reaktive.value.ReactiveVariable
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import kotlin.math.ceil

class TempoGridObjectView(override val obj: TempoGridObject, inst: ScoreObjectInstance) : ScoreObjectView(inst) {
    private val canvas = Canvas()
    private val marker = Line() styleClass "grid-marker-line"
    private lateinit var snapObserver: Observer
    private var barOffset = 0

    init {
        styleClass("tempo-grid")
        children.add(canvas)
        canvas.height = GRID_HEIGHT
        canvas.widthProperty().bind(Bindings.min(MAX_OBJECT_WIDTH, prefWidthProperty()))
        marker.endYProperty().bind(heightProperty())
    }

    override val borderColorWhenSelected: Color
        get() = Color.GREEN

    override fun initialize() {
        super.initialize()
        marker.visibleProperty().bind(context.project.uiState.snapEnabled.asObservableValue())
        repaint()
        val settings = context.project.uiState
        snapObserver = settings.snapOption.observe { _ -> repaint() }
            .and(settings.snapEnabled.observe { _ -> repaint() })
    }

    override fun configureInlineControls() {
        inlineControls.children.add(0, hspace(20.0)) //to avoid collision of name label with bar number
    }

    override fun setupDetailPane(pane: DetailPane) {
        val meter = obj.meter.get()
        if (meter == null) {
            pane.children.add(Label("Unresolved meter ${obj.meter.getName()}").styleClass("-fx-text-fill: red;"))
            return
        }
        Companion.setupMeterConfig(meter, pane, obj.context[UndoManager])

        val firstBarSpinner = IntSpinner(obj.firstBar, 0..1000)
            .setupUndo("First bar", obj.context[UndoManager])
            .minColumns(3)
        val nameButton = Button()
        nameButton.textProperty().bind(obj.meter.name.asObservableValue())
        pane.addItem("Meter", nameButton)
        pane.addItem("First bar:", firstBarSpinner)
    }

    override fun rescale() {
        super.rescale()
        repaint()
    }

    fun updatedConfig() {
        repaint()
    }

    override fun getDisplayHeight(): Double = GRID_HEIGHT

    private fun repaint() {
        val meter = obj.meter.get() ?: return
        val duration = parentPane.getDuration(canvas.width)
        val firstBar = obj.firstBar.now + barOffset
        paintGrid(context, meter, firstBar, duration.value, canvas, offset = zero)
    }

    override fun relocate(x: Double, y: Double) {
        super.relocate(x, y)
        if (prefWidth > MAX_OBJECT_WIDTH && layoutX < 0.0) {
            val meter = obj.meter.get() ?: return
            val barWidth = getWidth(meter.getDuration(TimeUnit.Bars))
            val offset = -layoutX
            barOffset = (offset / barWidth).toInt()
            canvas.translateX = barOffset * barWidth
            repaint()
        } else if (canvas.translateX != 0.0) {
            canvas.translateX = 0.0
            barOffset = 0
            repaint()
        }
    }

    fun unmark() {
        children.remove(marker)
    }

    fun mark(t: Decimal) {
        if (marker !in children) children.add(marker)
        marker.startX = getWidth(t)
        marker.endX = getWidth(t)
    }

    override fun inlineControlsBackground(
        controlsDisplay: ReactiveVariable<InlineControlsDisplay>,
    ): ObservableValue<Background> = SimpleObjectProperty(Background.EMPTY)

    companion object {
        private const val BAR_LINE_HEIGHT = 10.0
        private const val BEAT_LINE_HEIGHT = 6.0
        private const val TICK_LINE_HEIGHT = 3.0
        private const val MIN_BAR_NUMBER_DIST = 30.0
        const val GRID_HEIGHT = 30.0
        private const val CENTER_Y = 20.0

        fun paintGrid(
            context: Context, meter: MeterObject, firstBar: Int,
            duration: Double, area: Canvas, offset: Decimal
        ) = with(area.graphicsContext2D) {
            val width = canvas.width
            clearRect(0.0, 0.0, width, GRID_HEIGHT)

            val bpm = meter.beatsPerMinute.now
            val bpb = meter.beatsPerBar.now
            val tpb = meter.ticksPerBeat.now

            val tickDur = 60.0 / (bpm * tpb)
            val offsetTicks = (offset.value / tickDur).toInt()
            val offsetX = (offset.value / tickDur) - offsetTicks
            val ticks = ceil(duration / tickDur).toInt()
            val ticksPerBar = tpb * bpb
            val pixelsPerBeat = (width / duration / bpm * 60)
            val pixelsPerTick = pixelsPerBeat / tpb
            val secondsPerBar = (60 * bpb) / bpm.toDouble()
            val barNumberDist = secondsPerBar * (width / duration)

            val snapOption = context[UIState].snapOption.now
            val snapEnabled = context[UIState].snapEnabled.now

            for (tick in offsetTicks .. ticks + offsetTicks) {
                val x = (tick - offsetTicks) * pixelsPerTick - offsetX
                if (x > width) break
                when {
                    tick % ticksPerBar == 0 -> {
                        lineWidth = 3.0
                        stroke = if (snapOption <= TimeUnit.Bars && snapEnabled) Color.GREEN else Color.GRAY
                        strokeLine(x, CENTER_Y - BAR_LINE_HEIGHT, x, CENTER_Y + BAR_LINE_HEIGHT)

                        if (barNumberDist >= MIN_BAR_NUMBER_DIST) {
                            val bar = tick / ticksPerBar + firstBar
                            val text = (bar + firstBar).toString()
                            val textX = (x - 5).coerceAtLeast(0.0)
                            val y = 10.0
                            font = Font.font("Monospaced", 10.0)
                            lineWidth = 0.75
                            stroke = if (snapEnabled) Color.GREEN else Color.GRAY
                            strokeText(text, textX, y)
                        }
                    }

                    tick % tpb == 0 -> {
                        stroke = if (snapOption <= TimeUnit.Beats && snapEnabled) Color.GREEN else Color.GRAY
                        lineWidth = 2.0
                        strokeLine(x, CENTER_Y - BEAT_LINE_HEIGHT, x, CENTER_Y + BEAT_LINE_HEIGHT)
                    }

                    else -> {
                        strokeLine(x, CENTER_Y - TICK_LINE_HEIGHT, x, CENTER_Y + TICK_LINE_HEIGHT)
                    }
                }
            }

            stroke = if (snapEnabled) Color.GREEN else Color.GRAY
            lineWidth = 2.0
            strokeLine(0.0, CENTER_Y, width, CENTER_Y)
        }

        fun setupMeterConfig(meter: MeterObject, pane: DetailPane, undoManager: UndoManager?) {
            val bpmSpinner = IntSpinner(meter.beatsPerMinute, 10..500)
                .setupUndo("BPM", undoManager)
                .minColumns(3)
            val bpbSpinner = IntSpinner(meter.beatsPerBar, 1..24)
                .setupUndo("Beats per bar", undoManager)
                .minColumns(2)
            val tpbSpinner = IntSpinner(meter.ticksPerBeat, 1..24)
                .setupUndo("Ticks per beat", undoManager)
                .minColumns(2)
            pane.addItem("BPM:", bpmSpinner)
            pane.addItem("Beats", bpbSpinner)
            pane.addItem("Ticks", tpbSpinner)
        }
    }
}