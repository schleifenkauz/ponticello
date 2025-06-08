package ponticello.ui.score

import bundles.createBundle
import fxutils.control
import fxutils.prompt.DetailPane
import fxutils.styleClass
import javafx.beans.value.ObservableValue
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.paint.Color
import ponticello.model.project.InlineControlsDisplay
import ponticello.model.score.ScoreObjectGroup
import ponticello.model.score.ScoreObjectInstance
import ponticello.sc.view.ObjectSelectorControl
import reaktive.value.ReactiveBoolean
import reaktive.value.ReactiveVariable
import reaktive.value.binding.notEqualTo
import reaktive.value.fx.asObservableValue

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
        val topBar = Region() styleClass "score-object-top-bar"
        topBar.prefWidthProperty().bind(widthProperty())
        topBar.prefHeightProperty().bind(inlineControls.heightProperty())
        topBar.visibleProperty().bind(inlineControls.visibleProperty().not())
        children.add(topBar)
        scorePane = SubScorePane(instance, obj, parentPane, context)
        scorePane.layoutYProperty().bind(inlineControls.heightProperty())
        scorePane.prefWidthProperty().bind(widthProperty())
        scorePane.prefHeightProperty().bind(heightProperty().subtract(inlineControls.heightProperty()))
        children.add(scorePane)
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