package xenakis.ui

import javafx.scene.paint.Color
import xenakis.model.ScoreObjectGroup
import xenakis.model.ScoreObjectInstance

class ScoreObjectGroupView(inst: ScoreObjectInstance, val obj: ScoreObjectGroup) : ScoreObjectView(inst) {
    lateinit var scorePane: ScorePane
        private set

    override val borderColorWhenNotSelected: Color
        get() = Color.WHITE

    init {
        styleClass("sub-score")
    }

    override fun initialize(parent: ScorePane) {
        super.initialize(parent)
        scorePane = SubScorePane(obj, context)
        children.add(scorePane)
        scorePane.prefWidthProperty().bind(widthProperty())
        scorePane.prefHeightProperty().bind(heightProperty())
        widthProperty().addListener { _ -> rescale() }
        heightProperty().addListener { _ -> rescale() }
        scorePane.repaint()
    }

    override fun DetailPane.setupDetailPane() {
        addItem("Color:", colorPicker)
    }

    override fun rescale() {
        super.rescale()
        scorePane.repaint()
        for (view in scorePane.allViews) {
            view.rescale()
        }
    }
}