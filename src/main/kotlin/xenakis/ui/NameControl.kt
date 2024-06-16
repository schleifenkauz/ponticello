package xenakis.ui

import hextant.fx.registerShortcuts
import hextant.fx.setRoot
import javafx.scene.control.Control
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import reaktive.Observer
import reaktive.value.now
import xenakis.model.RenamableObject
import xenakis.sc.Identifier

class NameControl(val obj: RenamableObject) : Control() {
    private val field = TextField(obj.name.now).alwaysHGrow()
    private val btnEdit = Icon.Edit.button(action = "Edit name") { startEdit() }
    private val btnCommit = Icon.Check.button(action = "Commit change") { commitEdit() }
    private val root = HBox(field, btnEdit)
    private val observer: Observer

    init {
        root styleClass "name"
        field styleClass "name-field"
        setRoot(root)
        field.isEditable = false
        registerShortcuts {
            on("ENTER") { commitEdit() }
            on("ESCAPE") { abandonCommit() }
        }
        setOnMouseClicked { ev ->
            if (ev.clickCount >= 2 && !field.isEditable) startEdit()
        }
        observer = obj.name.observe { _, _, newName -> field.text = newName }
    }

    override fun requestFocus() {
        field.requestFocus()
    }

    fun startEdit() {
        if (field.isEditable) return
        field.isEditable = true
        root.children[1] = btnCommit
        field.requestFocus()
    }

    private fun commitEdit() {
        val name = field.text
        if (!field.isEditable) return
        if (Identifier.isValid(name) && obj.canRenameTo(name)) {
            field.isEditable = false
            val old = obj.name.now
            root.children[1] = btnEdit
            if (name != old) {
                obj.rename(name)
            }
        }
    }

    private fun abandonCommit() {
        if (!field.isEditable) return
        field.isEditable = false
        field.text = obj.name.now
        root.children[1] = btnEdit
    }
}