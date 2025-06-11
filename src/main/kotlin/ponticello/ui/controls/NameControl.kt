package ponticello.ui.controls

import fxutils.*
import fxutils.actions.Action
import fxutils.actions.ActionBar
import fxutils.actions.collectActions
import javafx.scene.control.TextField
import javafx.scene.input.KeyEvent
import javafx.scene.layout.HBox
import org.kordamp.ikonli.material2.Material2AL
import ponticello.model.obj.RenamableObject
import ponticello.model.registry.NamedObject.Companion.NO_NAME
import ponticello.sc.Identifier
import reaktive.Observer
import reaktive.value.ReactiveString
import reaktive.value.binding.flatMap
import reaktive.value.binding.not
import reaktive.value.now
import reaktive.value.reactiveValue
import reaktive.value.reactiveVariable

class NameControl(
    val obj: RenamableObject,
    private val defaultDisplayName: ReactiveString = reactiveValue(NO_NAME),
) : HBox() {
    private val field = TextField().alwaysHGrow() styleClass "name-field"
    val label = label(
        obj.name.flatMap { n -> if (n == NO_NAME) defaultDisplayName else reactiveValue(n) }
    ).alwaysHGrow() styleClass "name-label"

    private val observer: Observer

    val isEditing = reactiveVariable(false)

    private var isAutoSize = false

    init {
        styleClass("name-control")
        field.text = obj.name.now.takeIf { it != NO_NAME } ?: defaultDisplayName.now
        val toolbar = ActionBar(actions.withContext(this), buttonStyle = "small-icon-button")
        children.addAll(label, toolbar)
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
        field.autoSize(::isAutoSize)
        observer = obj.name.observe { _, _, newName ->
            field.text = newName.takeIf { it != NO_NAME } ?: defaultDisplayName.now
        } and defaultDisplayName.observe { _, _, defaultName ->
            if (obj.name.now == NO_NAME) field.text = defaultName
        }
    }

    fun autoSize() = also { isAutoSize = true }

    override fun requestFocus() {
        field.requestFocus()
    }

    fun startEdit() {
        if (isEditing.now) return
        children[0] = field
        isEditing.set(true)
        field.requestFocus()
    }

    private fun commitEdit() {
        if (!isEditing.now) return
        val name = field.text
        if (Identifier.isValid(name) && obj.canRenameTo(name)) {
            children[0] = label
            val old = obj.name.now
            if (name != old) {
                obj.rename(name)
            }
            isEditing.set(false)
        }
    }

    private fun abandonEdit() {
        if (!isEditing.now) return
        children[0] = label
        field.text = obj.name.now
        isEditing.set(false)
    }

    companion object {
        private val actions = collectActions<NameControl> {
            addAction("Edit name") {
                enableWhen { ctrl -> ctrl.isEditing.not() }
                ifNotApplicable(Action.IfNotApplicable.Hide)
                icon(Material2AL.EDIT)
                executes(NameControl::startEdit)
            }
            addAction("Commit edit") {
                enableWhen { ctrl -> ctrl.isEditing }
                ifNotApplicable(Action.IfNotApplicable.Hide)
                icon(Material2AL.CHECK)
                executes(NameControl::commitEdit)
            }
            addAction("Abandon edit") {
                enableWhen { ctrl -> ctrl.isEditing }
                //icon(Material2AL.CLOSE)
                executes(NameControl::abandonEdit)
            }
        }
    }
}