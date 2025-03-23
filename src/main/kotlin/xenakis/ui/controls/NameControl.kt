package xenakis.ui.controls

import fxutils.actions.ActionBar
import fxutils.actions.collectActions
import fxutils.alwaysHGrow
import fxutils.shortcut
import fxutils.styleClass
import javafx.scene.control.TextField
import javafx.scene.input.KeyEvent
import javafx.scene.layout.HBox
import org.kordamp.ikonli.material2.Material2AL
import reaktive.Observer
import reaktive.value.ReactiveString
import reaktive.value.binding.not
import reaktive.value.fx.asReactiveValue
import reaktive.value.now
import reaktive.value.observe
import reaktive.value.reactiveValue
import xenakis.model.obj.RenamableObject
import xenakis.model.registry.NamedObject.Companion.NO_NAME
import xenakis.sc.Identifier

class NameControl(
    val obj: RenamableObject,
    private val defaultDisplayName: ReactiveString = reactiveValue(NO_NAME)
) : HBox() {
    private val field = TextField(obj.name.now.takeIf { it != NO_NAME } ?: defaultDisplayName.now).alwaysHGrow()
    private val observer: Observer

    val isEditing = field.editableProperty().asReactiveValue()

    init {
        styleClass("name-control")
        field styleClass "name-field"
        val toolbar = ActionBar(actions.withContext(this), buttonStyle = "small-icon-button")
        children.addAll(field, toolbar)
        field.isEditable = false
        field.addEventFilter(KeyEvent.KEY_PRESSED) { ev ->
            if ("ENTER".shortcut.matches(ev)) {
                commitEdit()
                ev.consume()
            } else if ("ESCAPE".shortcut.matches(ev)) {
                abandonEdit()
                ev.consume()
            }
        }
        field.setOnMouseClicked { ev ->
            if (ev.clickCount >= 2 && !field.isEditable) startEdit()
        }
        field.focusedProperty().addListener { _, wasFocused, nowFocused ->
            if (wasFocused && !nowFocused) {
                abandonEdit()
            }
        }
        observer = obj.name.observe { _, _, newName ->
            field.text = newName.takeIf { it != NO_NAME } ?: defaultDisplayName.now
        } and defaultDisplayName.observe { _, _, defaultName ->
            if (obj.name.now == NO_NAME) field.text = defaultName
        }
    }

    override fun requestFocus() {
        field.requestFocus()
    }

    fun startEdit() {
        if (field.isEditable) return
        field.isEditable = true
        field.requestFocus()
    }

    private fun commitEdit() {
        val name = field.text
        if (!field.isEditable) return
        if (Identifier.isValid(name) && obj.canRenameTo(name)) {
            field.isEditable = false
            val old = obj.name.now
            if (name != old) {
                obj.rename(name)
            }
        }
    }

    private fun abandonEdit() {
        if (!field.isEditable) return
        field.isEditable = false
        field.text = obj.name.now
    }

    companion object {
        private val actions = collectActions<NameControl> {
            addAction("Edit name") {
                applicableIf { ctrl -> ctrl.isEditing.not() }
                icon(Material2AL.EDIT)
                executes(NameControl::startEdit)
            }
            addAction("Commit edit") {
                applicableIf { ctrl -> ctrl.isEditing }
                icon(Material2AL.CHECK)
                executes(NameControl::commitEdit)
            }
            addAction("Abandon edit") {
                applicableIf { ctrl -> ctrl.isEditing }
                icon(Material2AL.CLOSE)
                executes(NameControl::abandonEdit)
            }
        }
    }
}