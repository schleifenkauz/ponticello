package ponticello.ui.score

import fxutils.shortcut
import fxutils.styleClass
import fxutils.textArea
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.value.ObservableValue
import javafx.scene.Cursor
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
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
    private val textArea = textArea(obj.memoText) styleClass "memo-area"
    private val computeSize = Text()

    private val autosize: Observer

    private var isEditing = false

    init {
        children.setAll(textArea)
        addEventFilter(MouseEvent.MOUSE_CLICKED) { ev ->
            if (isEditing) return@addEventFilter
            if (ev.clickCount >= 2) enterEdit() else selectView(addToSelection = ev.isShiftDown)
            ev.consume()
        }
        addEventFilter(KeyEvent.KEY_PRESSED) { ev ->
            if ("ESCAPE".shortcut.matches(ev)) {
                exitEdit()
                ev.consume()
            }
        }
        textArea.focusedProperty().addListener { _, _, focused ->
            if (!focused) exitEdit()
        }
        textArea.prefWidthProperty().bind(prefWidthProperty())
        textArea.prefHeightProperty().bind(prefHeightProperty())

        autosize = obj.memoText.forEach { text ->
            computeSize.text = text
            resizedObject(obj)
        }
        computeSize.font = Font.font("Monospaced", 11.0)
    }

    fun enterEdit() {
        if (textArea.isEditable) return
        textArea.isEditable = true
        textArea.cursor = Cursor.DEFAULT
        textArea.selectAll()
        textArea.requestFocus()
    }

    private fun exitEdit() {
        if (!textArea.isEditable) return
        textArea.isEditable = false
        textArea.cursor = Cursor.TEXT
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