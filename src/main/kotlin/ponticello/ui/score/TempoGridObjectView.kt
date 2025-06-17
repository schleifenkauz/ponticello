package ponticello.ui.score

import fxutils.controls.IntSpinner
import fxutils.prompt.DetailPane
import fxutils.styleClass
import fxutils.undo.UndoManager
import hextant.context.Context
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.shape.Line
import javafx.scene.text.Text
import ponticello.impl.Decimal
import ponticello.model.obj.MeterObject
import ponticello.model.obj.project
import ponticello.model.project.UIState
import ponticello.model.project.settings
import ponticello.model.score.ScoreObjectInstance
import ponticello.model.score.TempoGridObject
import ponticello.model.score.TimeUnit
import reaktive.value.ReactiveValue
import reaktive.value.binding.and
import reaktive.value.binding.`if`
import reaktive.value.binding.lessThanOrEqualTo
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import reaktive.value.reactiveValue
import kotlin.math.ceil

class TempoGridObjectView(override val obj: TempoGridObject, inst: ScoreObjectInstance) : ScoreObjectView(inst) {
    private val area = Pane()
    private val marker = Line() styleClass "grid-marker-line"

    init {
        styleClass("tempo-grid")
        children.add(area)
        area.opacityProperty().bind(
            `if`(obj.context[UIState].snapEnabled, then = { 1.0 }, otherwise = { 0.5 }).asObservableValue()
        )
        marker.endYProperty().bind(heightProperty())
    }

    override val borderColorWhenSelected: Color
        get() = Color.GREEN

    override fun initialize() {
        super.initialize()
        marker.visibleProperty().bind(context.project.settings.snapEnabled.asObservableValue())
        repaint()
    }

    override fun setupDetailPane(pane: DetailPane) {
        if (!obj.meter.isResolved.now) {
            pane.children.add(Label("Unresolved meter ${obj.meter.getName()}").styleClass("-fx-text-fill: red;"))
            return
        }
        val bpmSpinner = IntSpinner(obj.beatsPerMinute, 10..500)
            .setupUndo("BPM", obj.context[UndoManager])
            .minColumns(3)
        val bpbSpinner = IntSpinner(obj.beatsPerBar, 1..24)
            .setupUndo("Beats per bar", obj.context[UndoManager])
            .minColumns(2)
        val tpbSpinner = IntSpinner(obj.ticksPerBeat, 1..24)
            .setupUndo("Ticks per beat", obj.context[UndoManager])
            .minColumns(2)
        val firstBarSpinner = IntSpinner(obj.firstBar, 0..1000)
            .setupUndo("First bar", obj.context[UndoManager])
            .minColumns(3)
        val nameButton = Button()
        nameButton.textProperty().bind(obj.meter.name.asObservableValue())
        pane.addItem("Meter", nameButton)
        pane.addItem("BPM:", bpmSpinner)
        pane.addItem("Beats", bpbSpinner)
        pane.addItem("Ticks", tpbSpinner)
        pane.addItem("First bar:", firstBarSpinner)
    }

    override fun rescale() {
        super.rescale()
        repaint()
    }

    fun updatedConfig() {
        repaint()
    }

    private fun repaint() {
        val meter = obj.meter.get() ?: return
        val duration = obj.duration
        val firstBar = obj.firstBar.now
        area.children.clear()
        paintGrid(context, meter, firstBar, duration.value, area, prefWidth, prefHeight)
        area
    }

    fun unmark() {
        area.children.remove(marker)
    }

    fun mark(t: Decimal) {
        if (marker !in area.children) area.children.add(marker)
        marker.startX = getWidth(t)
        marker.endX = getWidth(t)
    }

    override val defaultBackgroundColor: ReactiveValue<Color>
        get() = reactiveValue(Color.TRANSPARENT)

    companion object {
        private const val BAR_LINE_SPACE = 15.0
        private const val BEAT_LINE_SPACE = 0.3
        private const val TICK_LINE_SPACE = 0.4
        private const val MIN_BAR_NUMBER_DIST = 30.0

        fun paintGrid(
            context: Context, meter: MeterObject, firstBar: Int, duration: Double,
            area: Pane, width: Double, height: Double,
        ) {
            val bpm = meter.beatsPerMinute.now
            val bpb = meter.beatsPerBar.now
            val tpb = meter.ticksPerBeat.now
            val bars = ceil(duration * bpm / 60 / bpb).toInt()
            val pixelsPerBeat = (width / duration / bpm * 60)
            val secondsPerBar = (60 * bpb) / bpm.toDouble()
            val barNumberDist = secondsPerBar * (width / duration)
            val snapOption = context[UIState].snapOption
            val snapEnabled = context[UIState].snapEnabled
            for (bar in 0..bars) {
                val barX = pixelsPerBeat * bar * bpb
                if (barX > width) break
                if (barNumberDist > MIN_BAR_NUMBER_DIST) {
                    val barNumber = Text((barX - 5).coerceAtLeast(0.0), 12.0, (bar + firstBar).toString())
                        .styleClass("bar-number")
                    area.children.add(barNumber)
                }
                for (beat in 0 until bpb) {
                    val beatX = barX + (pixelsPerBeat * beat)
                    if (beatX > width) break
                    for (tick in 0 until tpb) {
                        val tickX = beatX + (pixelsPerBeat * tick) / tpb
                        if (tickX > width) break
                        val tickLine = Line(tickX, height * (1 - TICK_LINE_SPACE), tickX, height * TICK_LINE_SPACE)
                            .styleClass("tempo-line", "tick-line")
                        tickLine.strokeProperty().bind(
                            `if`(
                                snapOption.lessThanOrEqualTo(TimeUnit.Ticks) and snapEnabled,
                                then = { Color.GREEN }, otherwise = { Color.GRAY }
                            ).asObservableValue()
                        )
                        area.children.add(tickLine)
                    }
                    val beatLine = Line(beatX, height * (1 - BEAT_LINE_SPACE), beatX, height * BEAT_LINE_SPACE)
                        .styleClass("tempo-line", "beat-line")
                    beatLine.strokeProperty().bind(
                        `if`(
                            snapOption.lessThanOrEqualTo(TimeUnit.Beats) and snapEnabled,
                            then = { Color.GREEN }, otherwise = { Color.GRAY }
                        ).asObservableValue()
                    )
                    area.children.add(beatLine)
                }
                val barLine = Line(barX, BAR_LINE_SPACE, barX, height - BAR_LINE_SPACE)
                    .styleClass("tempo-line", "bar-line")
                barLine.strokeProperty().bind(
                    `if`(
                        snapOption.lessThanOrEqualTo(TimeUnit.Bars) and snapEnabled,
                        then = { Color.GREEN }, otherwise = { Color.GRAY }
                    ).asObservableValue()
                )
                area.children.add(barLine)
            }
            val horizontalLine = Line(0.0, height / 2, width, height / 2)
                .styleClass("tempo-line", "horizontal-line")
            area.children.add(horizontalLine)
        }
    }
}