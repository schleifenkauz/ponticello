package ponticello.ui.score

import fxutils.Ctrl
import fxutils.modifiers
import hextant.context.Context
import javafx.scene.input.MouseEvent
import ponticello.impl.*
import ponticello.model.flow.AudioFlowGroup
import ponticello.model.flow.AudioFlows
import ponticello.model.player.ScorePlayer
import ponticello.model.score.Score
import ponticello.ui.controls.NamePrompt
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import kotlin.math.exp

class NavigableScorePane(score: Score, context: Context) : RootScorePane(score, context) {
    override var displayStart: Decimal = 0.0.asTime

    override var displayEnd: Decimal = 0.0.asTime

    val displayedDuration get() = displayEnd - displayStart

    val displayRange get() = DecimalRange(displayStart, displayEnd)

    init {
        styleClass.add("score-view")
        heightProperty().addListener { _ -> repaint() }
        widthProperty().addListener { _, before, after ->
            if (displayedDuration <= zero) return@addListener
            val deltaX = after.toDouble() - before.toDouble()
            val deltaT = (deltaX / pixelsPerSecond).asTime
            displayEnd += deltaT
            context[ScorePlayer.CURRENT].playHead.updatePosition()
        }
    }

    fun displayWholeScore(): Future<Boolean> {
        val totalDuration = score.objectInstances.maxOfOrNull { obj -> obj.start + obj.duration } ?: 60.0.asTime
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
        return repaint()
    }

    override fun listenForEvents() {
        super.listenForEvents()
        setupNavigation()
    }

    override fun rightClicked(ev: MouseEvent) {
        super.rightClicked(ev)
        if (ev.modifiers == setOf(Ctrl)) {
            addFlowGroup(ev)
        }
    }

    private fun addFlowGroup(ev: MouseEvent) {
        val anchor = localToScreen(ev.x, ev.y) ?: return
        val name = NamePrompt(context[AudioFlows], "Name for new flow group", "")
            .showDialog(scene.window, anchor) ?: return
        val color = randomColor()
        val y = getScoreY(ev.y)
        val group = AudioFlowGroup.create(name, y, color)
        context[AudioFlows].add(group)
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
        display(displayStart + amount, displayEnd + amount)
        repaint()
    }
}