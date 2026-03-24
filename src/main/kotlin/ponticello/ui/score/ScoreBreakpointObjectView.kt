package ponticello.ui.score

import fxutils.setPseudoClassState
import fxutils.styleClass
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.value.ObservableValue
import javafx.scene.control.Tooltip
import javafx.scene.input.TransferMode
import javafx.scene.paint.Color
import javafx.scene.shape.Line
import ponticello.model.project.InlineControlsDisplay
import ponticello.model.registry.reference
import ponticello.model.score.ScoreBreakpointObject
import ponticello.model.score.ScoreObjectInstance
import reaktive.value.ReactiveVariable
import reaktive.value.fx.asObservableValue

class ScoreBreakpointObjectView(
    override val obj: ScoreBreakpointObject,
    instance: ScoreObjectInstance
) : ScoreObjectView(instance) {
    override val borderColorWhenSelected: Color get() = Color.TRANSPARENT
    override val borderColorWhenNotSelected: Color get() = Color.TRANSPARENT
    override val borderColorWhenSameObjectSelected: Color get() = Color.TRANSPARENT
    override val borderColorWhenFocused: Color get() = Color.TRANSPARENT

    override val hasDetailPane: Boolean get() = false
    private val line = Line() styleClass "breakpoint-line"

    override fun initialize() {
        super.initialize()
        line.stroke = Color.BLUE
        line.strokeWidth = 2.0
        line.endYProperty().bind(parentPane.heightProperty())
        val tooltip = Tooltip()
        tooltip.textProperty().bind(obj.name.asObservableValue())
        Tooltip.install(line, tooltip)
        line.setOnDragDetected { ev ->
            if (ev.isAltDown) {
                val db = line.startDragAndDrop(TransferMode.LINK)
                db.setContent(mapOf(ScoreBreakpointObject.DATA_FORMAT to obj.reference()))
                ev.consume()
            }
        }
        children.add(line)
    }

    override fun updateIsFocused(value: Boolean) {
        super.updateIsFocused(value)
        line.setPseudoClassState("focused", value)
    }

    override fun getDisplayWidth(): Double = 5.0

    override fun getDisplayHeight(): Double = parentPane.height

    override fun inlineControlsVisibilityCondition(
        controlsDisplay: ReactiveVariable<InlineControlsDisplay>
    ): ObservableValue<Boolean> = SimpleBooleanProperty(false)
}