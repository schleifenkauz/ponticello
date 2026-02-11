package ponticello.ui.score

import fxutils.centerChildren
import fxutils.styleClass
import fxutils.textArea
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.value.ObservableValue
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import javafx.scene.layout.HBox
import javafx.scene.text.Font
import javafx.scene.text.Text
import ponticello.model.project.InlineControlsDisplay
import ponticello.model.score.MemoObject
import ponticello.model.score.Score
import ponticello.model.score.ScoreObjectInstance
import reaktive.Observer
import reaktive.value.ReactiveVariable
import reaktive.value.forEach
import reaktive.value.now

class MemoObjectView(override val obj: MemoObject, inst: ScoreObjectInstance) : ScoreObjectView(inst) {
    private val edit = textArea(obj.memoText) styleClass "memo-area"
    private val display = textArea(obj.memoText) styleClass "memo-area"
    private val computeSize = Text()

    private val autosize: Observer

    private var isEditing = false

    init {
        children.setAll(HBox(display).centerChildren())
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

        autosize = obj.memoText.forEach { text ->
            computeSize.text = text
            resizedObject(obj)
        }
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
        if (obj.memoText.now.isBlank()) {
            instance.score!!.removeObject(instance, Score.RegistryOption.REMOVE_WITHOUT_ASKING)
        }
    }

    override fun getDisplayWidth(): Double = computeSize.prefWidth(-1.0) + 20

    override fun getDisplayHeight(): Double = computeSize.prefHeight(-1.0) + 10

    override fun inlineControlsVisibilityCondition(
        controlsDisplay: ReactiveVariable<InlineControlsDisplay>,
    ): ObservableValue<Boolean> = SimpleBooleanProperty(false)
}