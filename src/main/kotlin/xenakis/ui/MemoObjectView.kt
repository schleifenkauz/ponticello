package xenakis.ui

import hextant.fx.registerShortcuts
import javafx.scene.Cursor
import javafx.scene.control.Label
import javafx.scene.control.TextArea
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Priority
import xenakis.model.MemoObject
import xenakis.model.ScoreObjectInstance

class MemoObjectView(inst: ScoreObjectInstance, val obj: MemoObject) : ScoreObjectView(inst) {
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
    }

    override fun DetailPane.setupDetailPane() {
        addItem("Color: ", colorPicker)
    }

    override fun resizeObject(width: Double, height: Double, ev: MouseEvent, cursor: Cursor) {
        obj.width = width.coerceAtLeast(50.0)
        obj.resize(obj.duration, height)
    }

    override fun getDisplayWidth(): Double = obj.width

    fun textChanged(value: String) {
        if (value != textArea.text) textArea.text = value
        label.text = value
    }
}