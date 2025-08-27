package ponticello.sc.view

import bundles.Bundle
import bundles.createBundle
import fxutils.drag.DropHandler
import fxutils.drag.setupDropArea
import hextant.context.compoundEdit
import hextant.core.view.SimpleChoiceEditorControl
import javafx.scene.input.DragEvent
import javafx.scene.input.Dragboard
import javafx.scene.input.MouseEvent
import javafx.scene.input.TransferMode
import ponticello.model.registry.NamedObject
import ponticello.model.registry.ObjectReference
import ponticello.model.registry.reference
import ponticello.sc.editor.ObjectSelector
import ponticello.ui.registry.SearchableRegistryView
import reaktive.value.now

class ObjectSelectorControl<O : NamedObject>(
    private val selector: ObjectSelector<O>, arguments: Bundle = createBundle(),
) : SimpleChoiceEditorControl<ObjectReference<O>>(selector, arguments), DropHandler {
    init {
        root.setupDropArea(this)
        root.setOnDragDetected(::dragDetected)
    }

    @Suppress("unused")
    private fun dragDetected(ev: MouseEvent) {
        if (!selector.result.now.isResolved.now) return
        val format = selector.dataFormat() ?: return
        val db = startDragAndDrop(TransferMode.MOVE)
        db.setContent(mapOf(format to selector.result.now.name.now))
    }

    private fun extractObject(dragboard: Dragboard): O? {
        val name = dragboard.getContent(selector.dataFormat()) as? String ?: return null
        return selector.getList().getOrNull(name)
    }

    override fun canDrop(event: DragEvent): Boolean {
        val obj = extractObject(event.dragboard)
        return obj != null && obj != selector.result.now.get()
    }

    override fun drop(event: DragEvent): Boolean {
        val db = event.dragboard
        val obj = extractObject(db) ?: return false
        selector.select(obj.reference())
        return true
    }

    public override fun showChoicePopup() {
        val registry = selector.getList()
        val view = object : SearchableRegistryView<O>(registry, "Select ${registry.objectType}") {
            override fun createObject(name: String): O? = selector.createNewObject(name)

            override fun displayText(option: O): String = selector.toString(option).now
        }
        view.setFilter { obj -> selector.filter(obj) }
        context.compoundEdit("Select ${registry.objectType}") {
            val option = view.showPopup(anchorNode = this, initialOption = selector.result.now.get())
            if (option != null) selector.select(option.reference())
        }
    }
}