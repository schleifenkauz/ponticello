package xenakis.ui

import hextant.fx.registerShortcuts
import javafx.scene.control.ColorPicker
import javafx.scene.control.Label
import javafx.scene.control.TextArea
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Priority
import javafx.scene.paint.Color
import xenakis.model.MemoObject

class MemoObjectView(val obj: MemoObject) : ScoreObjectView(obj) {
    private val textArea = TextArea(obj.text) styleClass "memo-area"
    private val label = Label(obj.text) styleClass "memo-label"
    private val colorPicker = ColorPicker(obj.associatedColor ?: Color.BLACK) styleClass "button"

    override val supportedActions: List<Icon>
        get() = listOf(Icon.Delete)

    init {
        textArea.textProperty().addListener { _, _, newText ->
            obj.text = newText
        }
        colorPicker.valueProperty().addListener { _, _, color ->
            obj.associatedColor = color
        }
        setVgrow(textArea, Priority.ALWAYS)
        setVgrow(label, Priority.ALWAYS)
        label.setOnMouseClicked { ev ->
            if (ev.clickCount >= 2) {
                children.setAll(textArea)
            }
        }
        textArea.registerShortcuts {
            on("ESCAPE") {
                children.setAll(label)
            }
        }
        textArea.focusedProperty().addListener { _, _, focused ->
            if (!focused) children.setAll(label)
        }
    }

    override fun init(parent: ScoreView) {
        super.init(parent)
        children.add(label)
        header.children.add(1, colorPicker)
    }

    override fun setObjectWidth(width: Double, ev: MouseEvent, resizeFromLeft: Boolean) {
        obj.width = width.coerceAtLeast(50.0)
    }

    override fun getDisplayWidth(): Double = obj.width

    override fun recoloredObject() {
        super.recoloredObject()
        colorPicker.value = obj.associatedColor ?: Color.BLACK
    }

    fun textChanged(value: String) {
        if (value != textArea.text) textArea.text = value
        label.text = value
    }
}