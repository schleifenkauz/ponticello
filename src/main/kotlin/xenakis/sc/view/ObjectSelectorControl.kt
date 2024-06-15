package xenakis.sc.view

import bundles.Bundle
import hextant.core.view.EditorControl
import javafx.scene.Node
import javafx.scene.control.ComboBox
import javafx.scene.control.ListCell
import reaktive.Observer
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import xenakis.model.NamedObject
import xenakis.model.ObjectReference
import xenakis.model.ObjectRegistry
import xenakis.sc.editor.ObjectSelector
import xenakis.ui.showNamePrompt

class ObjectSelectorControl<O : NamedObject, R : ObjectReference<O>>(
    val editor: ObjectSelector<O, R>, arguments: Bundle
) : EditorControl<Node>(editor, arguments), ObjectRegistry.View<O>, ObjectSelectorView<O> {
    private val comboBox = ComboBox<Option<O>>()
    private val observers = mutableMapOf<O, Observer>()

    init {
        editor.registry.addView(this)
        comboBox.buttonCell = ObjectCell()
        comboBox.setCellFactory { ObjectCell() }
        comboBox.value = Option.Choose(editor.result.now.get())
        comboBox.items.add(Option.CreateNew)
        comboBox.valueProperty().addListener { _, last, value -> selected(last, value) }
        editor.addView(this)
    }

    override fun selected(obj: O) {
        comboBox.value = Option.Choose(obj)
    }

    private fun selected(last: Option<O>, new: Option<O>) {
        when (new) {
            Option.CreateNew -> {
                comboBox.selectionModel.select(last)
                showNamePrompt(editor.registry) { name ->
                    val obj = editor.createNewObject(name)
                    editor.registry.add(obj)
                    comboBox.selectionModel.select(Option.Choose(obj))
                }
            }

            is Option.Choose -> {
                @Suppress("UNCHECKED_CAST")
                editor.select(new.obj.createReference() as R)
            }
        }
    }

    override fun createDefaultRoot(): Node = comboBox

    override fun added(obj: O, idx: Int) {
        val canSelect = editor.canSelect(obj)
        observers[obj] = canSelect.observe { _, _, canSelectNow ->
            if (canSelectNow) addOption(obj)
            else comboBox.items.remove(Option.Choose(obj))
        }
        if (canSelect.now) addOption(obj)
    }

    private fun addOption(obj: O) {
        val idx = comboBox.items.size - 1
        comboBox.items.add(idx.coerceAtLeast(0), Option.Choose(obj))
    }

    override fun removed(obj: O, idx: Int) {
        observers.remove(obj)!!.kill()
        comboBox.items.remove(Option.Choose(obj))
    }

    private inner class ObjectCell : ListCell<Option<O>>() {
        override fun updateItem(item: Option<O>?, isEmpty: Boolean) {
            super.updateItem(item, isEmpty)
            if (textProperty().isBound) textProperty().unbind()
            when {
                isEmpty || item == null -> {
                    text = ""
                }

                item == Option.CreateNew -> {
                    text = "Create new"
                }

                item is Option.Choose -> {
                    val text = editor.extractText(item.obj)
                    textProperty().bind(text.asObservableValue())
                }
            }
        }
    }

    private sealed interface Option<out O : NamedObject> {
        data class Choose<O : NamedObject>(val obj: O) : Option<O>
        object CreateNew : Option<Nothing>
    }
}