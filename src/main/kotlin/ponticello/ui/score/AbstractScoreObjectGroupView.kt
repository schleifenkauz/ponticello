package ponticello.ui.score

import fxutils.actions.button
import fxutils.addAfter
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.scene.Node
import javafx.scene.layout.Background
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import ponticello.model.project.InlineControlsDisplay
import ponticello.model.score.ScoreObjectInstance
import reaktive.value.ReactiveVariable

abstract class AbstractScoreObjectGroupView(inst: ScoreObjectInstance) : ScoreObjectView(inst) {
    abstract val scorePane: ScorePane

    private val moveButton = MaterialDesignC.CURSOR_MOVE.button("Move", "drag-icon-button")

    override fun configureInlineControls() {
        inlineControls.children.addAfter(inlineNameLabel, moveButton)
        inlineControls.translateYProperty().bind(
            Bindings.`when`(layoutYProperty().greaterThan(inlineControls.heightProperty()))
                .then(inlineControls.heightProperty().negate())
                .otherwise(this.heightProperty())
        )
    }

    override fun inlineControlsVisibilityCondition(
        controlsDisplay: ReactiveVariable<InlineControlsDisplay>,
    ): ObservableValue<Boolean> = this.hoverProperty()
        .or(inlineNameLabel.pressedProperty())
        .or(moveButton.pressedProperty())

    override fun inlineControlsBackground(
        controlsDisplay: ReactiveVariable<InlineControlsDisplay>,
    ): ObservableValue<Background> = SimpleObjectProperty(Background.EMPTY)

    override fun dragTargets(): List<Node> = listOf(inlineNameLabel, moveButton)
}