package xenakis.ui

import javafx.scene.control.Label
import javafx.scene.control.Spinner
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.shape.Line
import javafx.scene.text.Text
import reaktive.value.ReactiveValue
import reaktive.value.fx.asObservableValue
import reaktive.value.fx.asProperty
import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.model.TempoGridObject
import xenakis.ui.XenakisController.Companion.currentProject
import kotlin.math.ceil

class TempoGridObjectView(val obj: TempoGridObject) : ScoreObjectView(obj) {
    private val area = Pane()
    private val marker = Line() styleClass "grid-marker-line"

    init {
        styleClass("tempo-grid")
        children.add(area)
        marker.endYProperty().bind(heightProperty())
    }

    override val supportedActions: List<Icon>
        get() = listOf(Icon.Delete)

    override val borderColorWhenSelected: Color
        get() = Color.GREEN

    override val nonSelectedBorderColor: Color
        get() = Color.TRANSPARENT

    override fun initialize(parent: ScorePane) {
        super.initialize(parent)
        createConfigurationBar(detailPane, obj)
        marker.visibleProperty().bind(context[currentProject].settings.snapEnabled.asObservableValue())
        repaint()
    }

    override fun rescale() {
        repaint()
    }

    fun updatedConfig() {
        repaint()
    }

    private fun repaint() {
        area.children.clear()
        val bpm = obj.beatsPerMinute.now
        val bpb = obj.beatsPerBar.now
        val tpb = obj.ticksPerBeat.now
        val bars = ceil(obj.duration * bpm / 60 / bpb).toInt()
        val barsPerSecond = bpm.toDouble() / bpb / 60
        for (bar in 0..bars) {
            val barX = getX(bar)
            if (barX > prefWidth) continue
            val barLine = Line(barX, BAR_LINE_SPACE, barX, prefHeight - BAR_LINE_SPACE)
                .styleClass("tempo-line", "bar-line")
            area.children.add(barLine)
            val barNumberDist = pane.pixelsPerSecond / barsPerSecond
            if (barNumberDist > MIN_BAR_NUMBER_DIST) {
                val barNumber = Text((barX - 5).coerceAtLeast(0.0), 12.0, bar.toString())
                    .styleClass("bar-number")
                area.children.add(barNumber)
            }
            for (beat in 0 until bpb) {
                val beatX = getX(bar, beat)
                if (beatX > prefWidth) continue
                val beatLine = Line(beatX, prefHeight * (1 - BEAT_LINE_SPACE), beatX, prefHeight * BEAT_LINE_SPACE)
                    .styleClass("tempo-line", "beat-line")
                area.children.add(beatLine)
                for (tick in 0 until tpb) {
                    val tickX = getX(bar, beat, tick)
                    if (tickX > prefWidth) continue
                    val tickLine = Line(tickX, prefHeight * (1 - TICK_LINE_SPACE), tickX, prefHeight * TICK_LINE_SPACE)
                        .styleClass("tempo-line", "tick-line")
                    area.children.add(tickLine)
                }
            }
        }
        val horizontalLine = Line(0.0, prefHeight / 2, prefWidth, prefHeight / 2)
            .styleClass("tempo-line", "horizontal-line")
        area.children.add(horizontalLine)
    }

    private fun getX(bar: Int, beat: Int = 0, tick: Int = 0): Double {
        val secondsPerBeat = 60.0 / obj.beatsPerMinute.now
        val t = ((bar * obj.beatsPerBar.now) + beat + (tick.toDouble() / obj.ticksPerBeat.now)) * secondsPerBeat
        return pane.getWidth(t)
    }

    fun unmark() {
        area.children.remove(marker)
    }

    fun mark(x: Double) {
        if (marker !in area.children) area.children.add(marker)
        marker.startX = x - layoutX
        marker.endX = x - layoutX
    }

    override val defaultBackgroundColor: ReactiveValue<Color>
        get() = reactiveValue(Color.TRANSPARENT)

    companion object {
        private const val BAR_LINE_SPACE = 15.0
        private const val BEAT_LINE_SPACE = 0.3
        private const val TICK_LINE_SPACE = 0.4
        private const val MIN_BAR_NUMBER_DIST = 30.0

        fun createConfigurationBar(layout: Pane, obj: TempoGridObject) {
            val bpmSpinner = Spinner<Int>(10, 500, 60).setFixedWidth(100.0)
            val bpbSpinner = Spinner<Int>(1, 24, 4).setFixedWidth(100.0)
            val tpbSpinner = Spinner<Int>(1, 24, 4).setFixedWidth(100.0)
            bpmSpinner.isEditable = true
            val labelBpm = Label("BPM:")
            val x = Label("x")
            bpmSpinner.valueFactory.valueProperty().bindBidirectional(obj.beatsPerMinute.asProperty())
            bpbSpinner.valueFactory.valueProperty().bindBidirectional(obj.beatsPerBar.asProperty())
            tpbSpinner.valueFactory.valueProperty().bindBidirectional(obj.ticksPerBeat.asProperty())
            layout.children.addAll(
                HBox(labelBpm, bpmSpinner).centerChildrenVertically(),
                HBox(bpbSpinner, x, tpbSpinner).centerChildrenVertically()
            )
        }
    }
}