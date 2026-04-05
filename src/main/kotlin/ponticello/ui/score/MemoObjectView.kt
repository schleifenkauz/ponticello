package ponticello.ui.score

import fxutils.label
import fxutils.shortcut
import fxutils.styleClass
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.value.ObservableValue
import javafx.scene.control.TextArea
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import javafx.scene.text.Font
import ponticello.model.project.InlineControlsDisplay
import ponticello.model.score.MemoObject
import ponticello.model.score.Score
import ponticello.model.score.ScoreObjectInstance
import reaktive.Observer
import reaktive.value.ReactiveVariable
import reaktive.value.binding.map
import reaktive.value.forEach
import reaktive.value.now

class MemoObjectView(override val obj: MemoObject, inst: ScoreObjectInstance) : ScoreObjectView(inst) {
    private val textArea = TextArea() styleClass "memo-area"

    //Add a space at the end of strings with \n as the last character to avoid the text area from being too small
    private val displayText = label(obj.memoText.map { txt -> if (txt.endsWith('\n')) "$txt " else txt })

    private val observer: Observer

    private var isEditing = false

    init {
        children.setAll(displayText)
        addEventFilter(MouseEvent.MOUSE_CLICKED) { ev ->
            if (isEditing) return@addEventFilter
            if (ev.clickCount >= 2) enterEdit()
            else selectView(addToSelection = ev.isShiftDown)
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

        displayText.layoutXProperty().bind(prefWidthProperty().subtract(displayText.widthProperty()).divide(2))
        displayText.layoutYProperty().bind(prefHeightProperty().subtract(displayText.heightProperty()).divide(2))

        observer = obj.memoText.forEach { text ->
            if (!isEditing) {
                textArea.text = text
            }
            resizedObject(obj)
        }
        textArea.textProperty().addListener { _, _, text ->
            if (isEditing) {
                obj.memoText.now = text
            }
        }
        displayText.font = Font.font("Monospaced", 11.0)
    }

    fun enterEdit() {
        if (isEditing) return
        isEditing = true
        children.setAll(textArea)
        textArea.selectAll()
        textArea.requestFocus()
    }

    private fun exitEdit() {
        if (!isEditing) return
        isEditing = false
        children.setAll(displayText)
        if (obj.memoText.now.isBlank()) {
            instance.score!!.removeObject(instance, Score.RegistryOption.REMOVE_WITHOUT_ASKING)
        }
    }

    override fun getDisplayWidth(): Double = displayText.prefWidth(-1.0) + 20

    override fun getDisplayHeight(): Double = displayText.prefHeight(-1.0) + 10

    override fun inlineControlsVisibilityCondition(
        controlsDisplay: ReactiveVariable<InlineControlsDisplay>,
    ): ObservableValue<Boolean> = SimpleBooleanProperty(false)
}