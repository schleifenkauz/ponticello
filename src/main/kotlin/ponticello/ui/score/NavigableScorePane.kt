package ponticello.ui.score

import hextant.context.Context
import ponticello.impl.*
import ponticello.model.score.Score
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import kotlin.math.exp

class NavigableScorePane(score: Score, context: Context) : RootScorePane(score, context) {
    override var displayStart: Decimal = 0.0.asTime

    override var displayEnd: Decimal = 0.0.asTime

    val displayedDuration get() = displayEnd - displayStart

    override val yRange: DecimalRange
        get() = zero..one

    init {
        styleClass.add("score-view")
        heightProperty().addListener { _ -> repaint() }
        widthProperty().addListener { _, before, after ->
            if (displayedDuration <= zero) return@addListener
            val deltaX = after.toDouble() - before.toDouble()
            val deltaT = (deltaX / pixelsPerSecond).asTime
            displayEnd += deltaT
            playHead.updatePosition()
        }
    }

    fun displayWholeScore(): Future<Boolean> {
        val totalDuration = score.objectInstances.maxOfOrNull { obj -> obj.end } ?: 60.0.asTime
        return display(zero, totalDuration)
    }

    fun display(start: Decimal, end: Decimal): Future<Boolean> {
        if (end < start) {
            Logger.severe("Attempt to display empty time range: $start .. $end", Logger.Category.Score)
            return CompletableFuture.completedFuture(false)
        }
        displayStart = start
        displayEnd = end
        if (displayStart < zero) {
            displayEnd -= displayStart
            displayStart = zero(4)
        }
        updatePixelsPerSecond()
        RectangleSelection.reposition()
        return repaint()
    }

    override fun listenForEvents() {
        super.listenForEvents()
        setupNavigation()
    }

    private fun setupNavigation() {
        setOnScroll { ev ->
            val delta = when {
                ev.deltaX != 0.0 -> ev.deltaX
                ev.deltaY != 0.0 -> ev.deltaY
                else -> return@setOnScroll
            }
            if (ev.isControlDown) {
                val factor = exp(-delta * 0.01)
                zoom(factor, ev.x)
            } else {
                scroll(-delta / pixelsPerSecond)
            }
        }
        setOnZoom { ev ->
            val factor = ev.zoomFactor
            zoom(factor, ev.x)
        }
    }

    fun zoom(amount: Double, evX: Double) {
        val newIntervalSize = (displayEnd - displayStart) * amount
        val oldIntervalCenter = (displayEnd + displayStart) / 2
        val newIntervalCenter = (getTime(evX) + oldIntervalCenter * 3) / 4
        display(newIntervalCenter - (newIntervalSize / 2), newIntervalCenter + (newIntervalSize / 2))
    }

    fun scroll(amount: Double) {
        display(displayStart + amount, displayEnd + amount)
    }
}