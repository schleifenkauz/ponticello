package xenakis.sc.view

import bundles.Bundle
import bundles.createBundle
import hextant.core.view.EditorControl
import hextant.core.view.TokenEditorView
import hextant.fx.HextantTextField
import hextant.fx.registerShortcuts
import javafx.scene.layout.HBox
import reaktive.event.biEvent
import reaktive.event.fire
import reaktive.value.now
import xenakis.sc.Identifier
import xenakis.sc.editor.IdentifierEditor
import xenakis.ui.Icon
import xenakis.ui.alwaysHGrow
import xenakis.ui.styleClass

class IdentifierEditorControl(private val editor: IdentifierEditor, arguments: Bundle = createBundle()) :
    EditorControl<HBox>(editor, arguments), TokenEditorView {
    private val field = HextantTextField(editor.text.now, autoSize = false).alwaysHGrow()
    private val btnEdit = Icon.Edit.button(action = "Edit identifier") { startEdit() }
    private val btnCommit = Icon.Check.button(action = "Commit change") { commitEdit() }

    private val textChange = biEvent<String, String>()

    val onChangeCommited = textChange.stream

    override fun createDefaultRoot(): HBox = HBox(field, btnEdit).styleClass("identifier-editor")

    init {
        field.isEditable = false
        registerShortcuts {
            on("ENTER") { commitEdit() }
            on("ESCAPE") { abandonCommit() }
        }
        setOnMouseClicked { ev ->
            if (ev.clickCount >= 2 && !field.isEditable) startEdit()
        }
        editor.addView(this)
    }

    override fun requestFocus() {
        field.requestFocus()
    }

    override fun focus() {
        requestFocus()
    }

    override fun displayText(newText: String) {
        field.text = newText
    }

    fun startEdit() {
        if (field.isEditable) return
        field.isEditable = true
        root.children[1] = btnCommit
        field.requestFocus()
    }

    private fun commitEdit() {
        val txt = field.text
        if (!field.isEditable || txt == editor.text.now) return
        if (Identifier.isValid(txt)) {
            field.isEditable = false
            val old = editor.text.now
            editor.setText(txt)
            textChange.fire(old, txt)
            root.children[1] = btnEdit
        }
    }

    private fun abandonCommit() {
        if (!field.isEditable) return
        field.isEditable = false
        field.text = editor.text.now
        root.children[1] = btnEdit
    }
}