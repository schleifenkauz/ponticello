package ponticello.ui.score

import bundles.createBundle
import fxutils.actions.button
import fxutils.addAfter
import fxutils.prompt.DetailPane
import fxutils.styleClass
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.scene.Node
import javafx.scene.layout.Background
import javafx.scene.layout.BackgroundFill
import javafx.scene.layout.CornerRadii
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import ponticello.model.project.InlineControlsDisplay
import ponticello.model.score.ScoreObjectGroup
import ponticello.model.score.ScoreObjectInstance
import ponticello.sc.view.ObjectSelectorControl
import reaktive.value.ReactiveVariable
import reaktive.value.binding.and
import reaktive.value.binding.map
import reaktive.value.binding.notEqualTo
import reaktive.value.fx.asObservableValue
import reaktive.value.fx.asReactiveValue

class ScoreObjectGroupView(
    override val obj: ScoreObjectGroup,
    inst: ScoreObjectInstance,
) : ScoreObjectView(inst) {
    lateinit var scorePane: SubScorePane
        private set

    private val moveButton = MaterialDesignC.CURSOR_MOVE.button("Move", "drag-icon-button")

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

        inlineControls.children.addAfter(inlineNameLabel, moveButton)
        inlineControls.translateYProperty().bind(
            Bindings.`when`(layoutYProperty().greaterThan(inlineControls.heightProperty()))
                .then(inlineControls.heightProperty().negate())
                .otherwise(this.heightProperty())
        )
    }

    override fun setupDetailPane(pane: DetailPane) {
        pane.addItem("Color:", this.colorPicker)
        pane.addItem("Default bus", ObjectSelectorControl(this.obj.busSelector, createBundle()))
    }

    override fun inlineControlsVisibilityCondition(
        controlsDisplay: ReactiveVariable<InlineControlsDisplay>,
    ): ObservableValue<Boolean> = (
            controlsDisplay.notEqualTo(InlineControlsDisplay.NONE)
                .and(
                    this.hoverProperty()
                        .or(inlineNameLabel.pressedProperty())
                        .or(moveButton.pressedProperty())
                        .asReactiveValue()
                )
            ).asObservableValue()

    override fun inlineControlsBackground(
        controlsDisplay: ReactiveVariable<InlineControlsDisplay>,
    ): ObservableValue<Background> = SimpleObjectProperty(Background.EMPTY)

    override fun dragTargets(): List<Node> = listOf(this, inlineNameLabel, moveButton)

    override fun rescale() {
        super.rescale()
        scorePane.repaint()
    }
}