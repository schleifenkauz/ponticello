package ponticello.ui.score

import bundles.createBundle
import fxutils.prompt.DetailPane
import fxutils.styleClass
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.scene.layout.Background
import javafx.scene.layout.BackgroundFill
import javafx.scene.layout.CornerRadii
import ponticello.model.project.InlineControlsDisplay
import ponticello.model.score.ScoreObjectGroup
import ponticello.model.score.ScoreObjectInstance
import ponticello.sc.view.ObjectSelectorControl
import reaktive.value.ReactiveBoolean
import reaktive.value.ReactiveVariable
import reaktive.value.binding.map
import reaktive.value.binding.notEqualTo
import reaktive.value.fx.asObservableValue

class ScoreObjectGroupView(
    override val obj: ScoreObjectGroup,
    inst: ScoreObjectInstance,
) : ScoreObjectView(inst) {
    lateinit var scorePane: SubScorePane
        private set

    init {
        styleClass("sub-score")
    }

    override fun initialize() {
        super.initialize()
        scorePane = SubScorePane(instance, obj, parentPane, context)
        scorePane.prefWidthProperty().bind(prefWidthProperty())
        scorePane.prefHeightProperty().bind(prefHeightProperty())
        scorePane.backgroundProperty().bind(backgroundColor.map { color ->
            Background(BackgroundFill(color, CornerRadii.EMPTY, null))
        }.asObservableValue())
        children.add(scorePane)
        scorePane.initialize()

    }

    override fun setupDetailPane(pane: DetailPane) {
        pane.addItem("Color:", this.colorPicker)
        pane.addItem("Default bus", ObjectSelectorControl(this.obj.busSelector, createBundle()))
    }

    override fun inlineControlsVisibilityCondition(
        controlsDisplay: ReactiveVariable<InlineControlsDisplay>,
    ): ReactiveBoolean = controlsDisplay.notEqualTo(InlineControlsDisplay.NONE)

    override fun inlineControlsBackground(
        controlsDisplay: ReactiveVariable<InlineControlsDisplay>,
    ): ObservableValue<Background> = SimpleObjectProperty(Background.EMPTY)

    override fun rescale() {
        super.rescale()
        scorePane.repaint()
    }
}