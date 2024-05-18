package xenakis.ui

import javafx.scene.control.ColorPicker
import javafx.scene.control.TextArea
import javafx.scene.input.MouseEvent
import javafx.scene.paint.Color
import xenakis.model.MemoObject

class MemoObjectView(override val obj: MemoObject) : ScoreObjectView() {
    private val textArea = TextArea(obj.text) styleClass "memo-area"
    private val colorPicker = ColorPicker(obj.associatedColor ?: Color.BLACK) styleClass "button"

    init {
        textArea.textProperty().addListener { _, _, newText ->
            obj.text = newText
        }
        colorPicker.valueProperty().addListener { _, _, color ->
            obj.associatedColor = color
        }
        contents.children.add(textArea)
        actions.children.add(0, colorPicker)
    }

    override fun setObjectWidth(width: Double, ev: MouseEvent, resizeFromLeft: Boolean) {
        obj.width = width.coerceAtLeast(50.0)
    }

    override fun getDisplayWidth(): Double = obj.width

    override fun recoloredObject() {
        colorPicker.value = obj.associatedColor ?: Color.BLACK
    }

    override val supportedActions: List<Icon>
        get() = listOf(Icon.Delete)

    fun textChanged(value: String) {
        if (value != textArea.text) textArea.text = value
    }
}