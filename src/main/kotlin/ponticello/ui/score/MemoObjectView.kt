package ponticello.ui.score

import fxutils.centerChildren
import fxutils.prompt.DetailPane
import fxutils.styleClass
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.value.ObservableValue
import javafx.scene.control.Label
import javafx.scene.control.TextArea
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import javafx.scene.layout.HBox
import javafx.scene.text.Font
import javafx.scene.text.Text
import ponticello.model.project.InlineControlsDisplay
import ponticello.model.score.MemoObject
import ponticello.model.score.ScoreObjectInstance
import reaktive.value.ReactiveVariable

class MemoObjectView(override val obj: MemoObject, inst: ScoreObjectInstance) : ScoreObjectView(inst) {
    private val edit = TextArea(obj.text) styleClass "memo-area"
    private val display = Label(obj.text) styleClass "memo-area"
    private val computeSize = Text(obj.text)

    private var isEditing = false

    init {
        children.setAll(HBox(display).centerChildren())
        edit.textProperty().addListener { _, _, text ->
            if (obj.text != text) obj.text = text
        }
        addEventFilter(MouseEvent.MOUSE_CLICKED) { ev ->
            if (isEditing) return@addEventFilter
            if (ev.clickCount >= 2) enterEdit() else selectView(addToSelection = ev.isShiftDown)
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
        edit.prefWidthProperty().bind(prefWidthProperty())
        edit.prefHeightProperty().bind(prefHeightProperty())
        computeSize.font = Font.font("Monospaced", 11.0)
    }

    fun enterEdit() {
        if (isEditing) return
        children.setAll(HBox(edit).centerChildren())
        edit.selectAll()
        edit.requestFocus()
        isEditing = true
    }

    private fun exitEdit() {
        if (!isEditing) return
        children.setAll(HBox(display).centerChildren())
        isEditing = false
    }

    override fun setupDetailPane(pane: DetailPane) {
        pane.addItem("Color: ", this.colorPicker)
    }

    override fun getDisplayWidth(): Double = computeSize.prefWidth(-1.0) + 20

    override fun getDisplayHeight(): Double = computeSize.prefHeight(-1.0) + 10

    fun textChanged(value: String) {
        if (value != edit.text) {
            edit.text = value
        }
        display.text = value
        computeSize.text = value
        resizedObject(obj)
    }

    override fun inlineControlsVisibilityCondition(
        controlsDisplay: ReactiveVariable<InlineControlsDisplay>,
    ): ObservableValue<Boolean> = SimpleBooleanProperty(false)
}