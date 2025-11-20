package ponticello.ui.score

import javafx.scene.canvas.Canvas
import javafx.scene.paint.Color
import javafx.scene.shape.Line
import javafx.scene.text.Font
import ponticello.impl.*
import ponticello.model.obj.project
import ponticello.model.project.UIState
import ponticello.model.project.uiState
import ponticello.model.score.MeterObject
import ponticello.model.score.ObjectPosition
import ponticello.model.score.ScoreObject
import ponticello.model.score.TimeUnit
import reaktive.value.binding.and
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import reaktive.value.reactiveVariable
import kotlin.math.*

data class TempoGrid(
    val type: GridType,
    val scoreObject: ScoreObject,
    private val getPosition: () -> ObjectPosition,
    val meter: MeterObject,
    val scale: Decimal,
    val offset: Decimal,
    private val canvas: Canvas,
    val marker: Line,
) {
    val position get() = getPosition()
    val yPosition get() = position.y
    val gridStart get() = position.time
    val gridEnd get() = gridStart + scoreObject.duration

    val timeRange: DecimalRange get() = gridStart..gridEnd

    val bpm get() = meter.beatsPerMinute.now * scale

    val context get() = scoreObject.context

    private val marked = reactiveVariable(false)

    init {
        marker.visibleProperty().bind(
            context.project.uiState.snapEnabled.and(marked).asObservableValue()
        )
    }

    fun mark(x: Double) {
        marked.set(true)
        marker.startX = x
        marker.endX = x
    }

    fun unmark() {
        marked.set(false)
    }

    fun snapToGrid(time: Decimal, unit: TimeUnit): Decimal {
        val t = time - gridStart + offset
        val unitDur = meter.getDuration(unit) / scale
        val snapped = (t / unitDur).roundToInt() * unitDur
        return snapped + gridStart - offset
    }

    fun paintGrid(pixelsPerSecond: Double, extraOffset: Decimal = zero) = with(canvas.graphicsContext2D) {
        clearRect(0.0, 0.0, canvas.width, canvas.height)

        val bpm = meter.beatsPerMinute.now.value
        val bpb = meter.beatsPerBar.now
        val tpb = meter.ticksPerBeat.now

        if (bpm == 0.0 || bpb == 0 || tpb == 0) return@with

        val totalOffset = offset + extraOffset
        val totalDuration = scoreObject.duration.value
        val beatDur = 60.0 / bpm / scale.value
        val barDur = beatDur * bpb
        val tickDur = beatDur / tpb
        val offsetTicks = ceil(totalOffset.value / tickDur).toInt()
        val ticks = ceil(totalDuration / tickDur).toInt()
        val ticksPerBar = tpb * bpb

        val barWidth = barDur * pixelsPerSecond
        val beatWidth = beatDur * pixelsPerSecond
        val tickWidth = tickDur * pixelsPerSecond
        val offsetX = totalOffset.value * pixelsPerSecond

        val snapOption = context[UIState].snapOption.now
        val snapEnabled = context[UIState].snapEnabled.now

        if (barWidth <= 0.01 || barWidth.isNaN()) return@with

        val barDistance = 2.0.pow(round(log2(25 / barWidth).coerceAtLeast(0.0))).roundToInt()
        val barNumberDistance = 2.0.pow(round(log2(60 / barWidth).coerceAtLeast(0.0))).roundToInt()

        for (tick in offsetTicks..ticks + offsetTicks) {
            val x = tick * tickWidth - offsetX
            if (tick * tickDur - offset.value > scoreObject.duration.value) break
            when {
                tick % ticksPerBar == 0 -> {
                    if ((tick / ticksPerBar) % barDistance != 0) continue
                    lineWidth = 3.0
                    stroke = if (snapOption <= TimeUnit.Bars && snapEnabled) Color.GREEN else Color.GRAY
                    when (type) {
                        GridType.Regular -> strokeLine(x, CENTER_Y - BAR_LINE_HEIGHT, x, CENTER_Y + BAR_LINE_HEIGHT)
                        GridType.SampleOverlay -> strokeLine(x, 10.0, x, canvas.height)
                    }

                    if ((tick / ticksPerBar) % barNumberDistance == 0) {
                        val bar = tick / ticksPerBar
                        val text = bar.toString()
                        val textX = (x - 5).coerceAtLeast(0.0)
                        val y = 10.0
                        font = Font.font("Monospaced", 10.0)
                        lineWidth = 0.75
                        stroke = if (snapEnabled) Color.GREEN else Color.GRAY
                        strokeText(text, textX, y)
                    }
                }

                tick % tpb == 0 -> {
                    if (beatWidth < MIN_BEAT_WIDTH) continue
                    stroke = if (snapOption <= TimeUnit.Beats && snapEnabled) Color.GREEN else Color.GRAY
                    lineWidth = 2.0
                    when (type) {
                        GridType.Regular -> strokeLine(x, CENTER_Y - BEAT_LINE_HEIGHT, x, CENTER_Y + BEAT_LINE_HEIGHT)
                        GridType.SampleOverlay -> strokeLine(x, canvas.height * 0.6, x, canvas.height)
                    }
                }

                else -> {
                    if (tickWidth < MIN_TICK_WIDTH) continue
                    stroke = if (snapOption <= TimeUnit.Ticks && snapEnabled) Color.GREEN else Color.GRAY
                    lineWidth = 1.0
                    when (type) {
                        GridType.Regular -> strokeLine(x, CENTER_Y - TICK_LINE_HEIGHT, x, CENTER_Y + TICK_LINE_HEIGHT)
                        GridType.SampleOverlay -> strokeLine(x, canvas.height * 0.75, x, canvas.height)
                    }
                }
            }
        }

        if (type == GridType.Regular) {
            stroke = if (snapEnabled) Color.GREEN else Color.GRAY
            lineWidth = 2.0
            strokeLine(0.0, CENTER_Y, canvas.width, CENTER_Y)
        }
    }


    enum class GridType {
        Regular, SampleOverlay
    }

    companion object {
        private const val BAR_LINE_HEIGHT = 10.0
        private const val BEAT_LINE_HEIGHT = 6.0
        private const val TICK_LINE_HEIGHT = 3.0
        private const val MIN_BAR_NUMBER_DIST = 30.0
        private const val MIN_BAR_WIDTH = 10.0
        private const val MIN_BEAT_WIDTH = 8.0
        private const val MIN_TICK_WIDTH = 5.0
        const val GRID_HEIGHT = 30.0
        private const val CENTER_Y = 20.0
    }
}