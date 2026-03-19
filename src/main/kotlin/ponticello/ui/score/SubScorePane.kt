package ponticello.ui.score

import ponticello.impl.*
import ponticello.model.score.ObjectPosition
import ponticello.model.score.Score
import ponticello.model.score.ScoreObject

class SubScorePane(
    score: Score,
    override val associatedView: ScoreObjectView,
) : AbstractScorePane(score, associatedView.context) {
    val parentPane get() = associatedView.parentPane

    override val associatedObject: ScoreObject get() = associatedView.instance.obj

    override val root: ScorePane get() = parentPane.root

    override val displayStart: Decimal get() = 0.0.asTime

    override val displayEnd: Decimal get() = associatedObject.duration

    override val yRange: DecimalRange
        get() = zero..associatedObject.height

    override val pixelsPerSecond: Double
        get() = parentPane.pixelsPerSecond

    override val absolutePosition: ObjectPosition
        get() = parentPane.absolutePosition + associatedView.instance.position

    fun initialize() {
        listenForEvents()
        score.addListener(this)
    }

    override fun getScreenY(scoreY: Decimal): Double = scoreY.value * (this.prefHeight / associatedObject.height.value)

    override fun getScoreY(screenY: Double): Decimal = screenY * (associatedObject.height / this.prefHeight)
}