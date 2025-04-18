package xenakis.ui.score

import bundles.createBundle
import fxutils.prompt.DetailPane
import fxutils.styleClass
import javafx.scene.paint.Color
import xenakis.model.score.ScoreObjectGroup
import xenakis.model.score.ScoreObjectInstance
import xenakis.sc.view.ObjectSelectorControl

class ScoreObjectGroupView(private val inst: ScoreObjectInstance, val obj: ScoreObjectGroup) : ScoreObjectView(inst) {
    lateinit var scorePane: ScorePane
        private set

    override val borderColorWhenNotSelected: Color
        get() = Color.WHITE

    init {
        styleClass("sub-score")
    }

    override fun initialize() {
        super.initialize()
        scorePane = if (pane != null) SubScorePane(inst, obj, pane, context)
        else ScoreView(obj.score, context)
        children.add(scorePane)
        scorePane.prefWidthProperty().bind(widthProperty())
        scorePane.prefHeightProperty().bind(heightProperty())
        scorePane.repaint()
    }

    override fun setupDetailPane(pane: DetailPane) {
        pane.addItem("Color:", this.colorPicker)
        pane.addItem("Default group", ObjectSelectorControl(this.obj.groupSelector, createBundle()))
        pane.addItem("Default bus", ObjectSelectorControl(this.obj.busSelector, createBundle()))
    }

    override fun rescale() {
        super.rescale()
        scorePane.repaint()
        for (view in scorePane.allViews) {
            view.rescale()
        }
    }
}