package ponticello.ui.score

import fxutils.Ctrl
import fxutils.modifiers
import hextant.context.Context
import javafx.geometry.Point2D
import javafx.scene.input.MouseEvent
import ponticello.impl.*
import ponticello.model.flow.AudioFlowGroup
import ponticello.model.flow.AudioFlows
import ponticello.model.player.ScorePlayer
import ponticello.model.score.Score
import ponticello.ui.controls.NamePrompt
import kotlin.math.exp

class NavigableScorePane(score: Score, context: Context) : RootScorePane(score, context) {
    override var displayStart: Decimal = 0.0.asTime

    override var displayEnd: Decimal = 0.0.asTime

    val displayedDuration get() = displayEnd - displayStart

    val displayRange get() = DecimalRange(displayStart, displayEnd)

    init {
        styleClass.add("score-view")
        heightProperty().addListener { _ -> repaint() }
        widthProperty().addListener { _ ->
            context[ScorePlayer.CURRENT].playHead.updatePosition()
        }
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
        val y = getScoreY(ev.y)
        val name = NamePrompt(context[AudioFlows], "Name for new flow group", "")
            .showDialog(scene.window, Point2D(ev.x, ev.y)) ?: return
        val color = randomColor()
        val group = AudioFlowGroup.create(name, y, color)
        context[AudioFlows].add(group)
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