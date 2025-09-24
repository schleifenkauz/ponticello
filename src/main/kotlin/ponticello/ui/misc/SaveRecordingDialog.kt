package ponticello.ui.misc

import fxutils.centerChildren
import fxutils.controls.CheckBox
import fxutils.prompt.ConfirmablePrompt
import fxutils.styleClass
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import java.nio.file.InvalidPathException
import java.nio.file.Paths

class SaveRecordingDialog(defaultFileName: String) : ConfirmablePrompt<SaveRecordingDialog.Response>(
    "Save recording?", cancelText = "No", confirmText = "Yes"
) {
    private val fileNameField = TextField(defaultFileName) styleClass "sleek-text-field"
    private val addBufferOption = CheckBox(state = false, "Add as sample")
    override val content = HBox(5.0, fileNameField, addBufferOption).centerChildren()

    init {
        content.prefWidth = 300.0
        HBox.setHgrow(fileNameField, Priority.ALWAYS)
        confirmButton.disableProperty().bind(fileNameField.textProperty().map { text -> !isValidFileName(text) })
    }

    override fun confirm(): Response = Response(fileNameField.text, addBufferOption.isSelected)

    data class Response(val fileName: String, val addAsBuffer: Boolean)

    companion object {
        private fun isValidFileName(str: String): Boolean {
            if (str.isBlank()) return false
            try {
                Paths.get(str)
                return true
            } catch (e: InvalidPathException) {
                return false
            }
        }
    }
}