package xenakis.ui

import javafx.scene.control.ColorPicker
import javafx.scene.control.TextArea
import javafx.scene.input.MouseEvent
import xenakis.model.MemoObject
import xenakis.model.XenakisProject

class MemoObjectView(override val obj: MemoObject, project: XenakisProject) : ScoreObjectView(obj, project) {
    private val textArea = TextArea(obj.text) styleClass "memo-area"
    private val colorPicker = ColorPicker(obj.color) styleClass "button"

    init {
        textArea.textProperty().addListener { _, _, newText ->
            obj.text = newText
        }
        colorPicker.valueProperty().addListener { _, _, color ->
            obj.color = color
            contents.border = solidBorder(color, 2.0, 3.0)
        }
        contents.children.add(textArea)
    }

    override fun setObjectWidth(width: Double, ev: MouseEvent, resizeFromLeft: Boolean) {
        obj.width = width.coerceAtLeast(50.0)
    }

    override fun getDisplayWidth(): Double = obj.width

    override fun setupHeader() {
        setupHeader(Icon.Delete)
        actions.children.add(0, colorPicker)
    }
}