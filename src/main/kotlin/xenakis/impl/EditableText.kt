package xenakis.impl

import javafx.beans.property.Property
import javafx.beans.value.ChangeListener
import javafx.beans.value.WeakChangeListener
import javafx.geometry.Insets
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.input.KeyCode
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Pane

class EditableText(private val property: Property<String>) : Pane() {
    val label = Label(property.value)
    val textField = TextField()
    private val propertyListener: ChangeListener<String>

    val text: String get() = property.value

    init {
        textField.styleClass.add("editable-text")
        children.setAll(label)
        propertyListener = ChangeListener { _, _, newText ->
            textField.text = newText
            label.text = newText
        }
        property.addListener(WeakChangeListener(propertyListener))
        listenForAction()
    }

    fun configureWidth(value: Double): EditableText {
        prefWidth = value
        textField.prefWidth = value
        label.prefWidth = value
        return this
    }

    fun configurePadding(forLabel: Double, forTextField: Double): EditableText {
        textField.padding = Insets(forTextField)
        this.label.padding = Insets(forLabel, forLabel, 0.0, 0.0)
        return this
    }

    private fun listenForAction() {
        textField.setOnAction { ev ->
            label.prefWidth /= 1.5
            property.value = textField.text
            children.setAll(label)
            ev.consume()
        }
        label.addEventFilter(MouseEvent.MOUSE_RELEASED) { ev ->
            if (ev.button == MouseButton.SECONDARY) {
                label.prefWidth *= 1.5
                children.setAll(textField)
                textField.text = property.value
                textField.requestFocus()
                textField.selectAll()
                ev.consume()
            }
        }
        textField.setOnKeyReleased { ev ->
            if (ev.code == KeyCode.ESCAPE) {
                label.prefWidth /= 1.5
                children.setAll(label)
            }
        }
        textField.focusedProperty().addListener { _, _, focused ->
            if (!focused && children[0] == textField) {
                label.prefWidth /= 1.5
                children.setAll(label)
            }
        }
    }

}