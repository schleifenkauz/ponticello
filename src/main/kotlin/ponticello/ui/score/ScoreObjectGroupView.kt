package ponticello.ui.score

import bundles.createBundle
import fxutils.prompt.DetailPane
import fxutils.styleClass
import javafx.scene.paint.Color
import ponticello.model.score.ScoreObjectGroup
import ponticello.model.score.ScoreObjectInstance
import ponticello.sc.view.ObjectSelectorControl

class ScoreObjectGroupView(
    override val obj: ScoreObjectGroup,
    inst: ScoreObjectInstance,
) : ScoreObjectView(inst) {
    lateinit var scorePane: ScorePane
        private set

    override val borderColorWhenNotSelected: Color
        get() = Color.WHITE

    init {
        styleClass("sub-score")
    }

    override fun initialize() {
        super.initialize()
        scorePane = SubScorePane(instance, obj, parentPane, context)
        children.add(scorePane)
        scorePane.prefWidthProperty().bind(widthProperty())
        scorePane.prefHeightProperty().bind(heightProperty().subtract(inlineControls.heightProperty()))
        scorePane.layoutYProperty().bind(inlineControls.heightProperty())
    }

    override fun setupDetailPane(pane: DetailPane) {
        pane.addItem("Color:", this.colorPicker)
        pane.addItem("Default bus", ObjectSelectorControl(this.obj.busSelector, createBundle()))
    }

    override fun rescale() {
        super.rescale()
        scorePane.repaint()
    }
}