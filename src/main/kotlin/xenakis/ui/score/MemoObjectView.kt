package xenakis.ui.score

import fxutils.centerChildren
import fxutils.prompt.DetailPane
import fxutils.styleClass
import javafx.scene.control.Label
import javafx.scene.control.TextArea
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import javafx.scene.layout.HBox
import javafx.scene.text.Text
import xenakis.model.score.MemoObject
import xenakis.model.score.ScoreObjectInstance

class MemoObjectView(inst: ScoreObjectInstance, override val obj: MemoObject) : ScoreObjectView(inst) {
    private val edit = TextArea(obj.text) styleClass "memo-area"
    private val display = Label(obj.text) styleClass "memo-area"
    private val computeSize = Text(obj.text)

    init {
        exitEdit()
        edit.textProperty().addListener { _, _, text ->
            if (obj.text != text) obj.text = text
        }
        addEventFilter(MouseEvent.MOUSE_CLICKED) { ev ->
            if (ev.clickCount >= 2) enterEdit() else selectThis(addToSelection = ev.isShiftDown)
            ev.consume()
        }
        addEventFilter(KeyEvent.KEY_PRESSED) { ev ->
            if (ev.code == KeyCode.ESCAPE) {
                exitEdit()
                ev.consume()
            }
        }
        edit.focusedProperty().addListener { _, _, focused ->
            if (!focused) exitEdit()
        }
    }

    fun enterEdit() {
        if (edit in children) return
        children.setAll(HBox(edit).centerChildren())
        edit.selectAll()
        edit.requestFocus()
    }

    private fun exitEdit() {
        if (display in children) return
        children.setAll(HBox(display).centerChildren())
    }

    override fun setupDetailPane(pane: DetailPane) {
        pane.addItem("Color: ", this.colorPicker)
    }

    override fun getDisplayWidth(): Double = computeSize.prefWidth(-1.0) + 15

    override fun getDisplayHeight(): Double = computeSize.prefHeight(-1.0) + 10

    fun textChanged(value: String) {
        if (value != edit.text) {
            edit.text = value
        }
        display.text = value
        computeSize.text = value
        resizedObject(obj)
    }
}