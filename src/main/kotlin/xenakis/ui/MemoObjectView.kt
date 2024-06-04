package xenakis.ui

import hextant.fx.registerShortcuts
import javafx.scene.Cursor
import javafx.scene.control.Label
import javafx.scene.control.TextArea
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Priority
import xenakis.model.MemoObject

class MemoObjectView(val obj: MemoObject) : ScoreObjectView(obj) {
    private val textArea = TextArea(obj.text) styleClass "memo-area"
    private val label = Label(obj.text) styleClass "memo-label"

    override val supportedActions: List<Icon>
        get() = listOf(Icon.Delete)

    init {
        textArea.textProperty().addListener { _, _, newText ->
            obj.text = newText
        }
        setVgrow(textArea, Priority.ALWAYS)
        setVgrow(label, Priority.ALWAYS)
        setOnMouseClicked { ev ->
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

    override fun initialize(parent: ScorePane) {
        super.initialize(parent)
        children.add(label)
        header.children.add(1, colorPicker)
    }

    override fun resizeObject(width: Double, height: Double, ev: MouseEvent, cursor: Cursor) {
        obj.width = width.coerceAtLeast(50.0)
        obj.height = height.coerceAtLeast(50.0)
    }

    override fun getDisplayWidth(): Double = obj.width

    fun textChanged(value: String) {
        if (value != textArea.text) textArea.text = value
        label.text = value
    }
}