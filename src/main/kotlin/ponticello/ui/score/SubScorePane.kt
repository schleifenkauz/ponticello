package ponticello.ui.score

import hextant.context.Context
import ponticello.impl.*
import ponticello.model.score.ObjectPosition
import ponticello.model.score.ScoreObjectGroup
import ponticello.model.score.ScoreObjectInstance

class SubScorePane(
    private val instance: ScoreObjectInstance,
    override val associatedObject: ScoreObjectGroup,
    override val associatedView: ScoreObjectGroupView,
    context: Context,
) : AbstractScorePane(associatedObject.score, context) {
    val parentPane get() = associatedView.parentPane

    override val root: ScorePane
        get() = parentPane.root
    override val displayStart: Decimal
        get() = 0.0.asTime
    override val displayEnd: Decimal
        get() = associatedObject.duration

    override val yRange: DecimalRange
        get() = zero..associatedObject.height

    override val pixelsPerSecond: Double
        get() = parentPane.pixelsPerSecond

    override val absolutePosition: ObjectPosition
        get() = parentPane.absolutePosition + instance.position

    fun initialize() {
        listenForEvents()
        associatedObject.score.addListener(this)
    }

    override fun getScreenY(scoreY: Decimal): Double = scoreY.value * (this.prefHeight / associatedObject.height.value)

    override fun getScoreY(screenY: Double): Decimal = screenY * (associatedObject.height / this.prefHeight)
}