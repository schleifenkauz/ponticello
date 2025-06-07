package ponticello.sc.view

import bundles.Bundle
import bundles.createBundle
import fxutils.setupDropArea
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
) : SimpleChoiceEditorControl<ObjectReference<O>>(selector, arguments) {
    init {
        root.setupDropArea(::canDrop, ::onDrop)
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

    private fun canDrop(dragboard: Dragboard): Boolean {
        val obj = extractObject(dragboard)
        return obj != null && obj != selector.result.now.get()
    }

    private fun onDrop(ev: DragEvent) {
        val db = ev.dragboard
        val obj = extractObject(db) ?: return
        selector.select(obj.reference())
    }

    public override fun showChoicePopup() {
        val registry = selector.getList()
        val view = object : SearchableRegistryView<O>(registry, "Select ${registry.objectType}") {
            override fun createObject(name: String): O? = selector.createNewObject(name)

            override fun displayText(option: O): String = selector.toString(option).now
        }
        view.setFilter { obj -> selector.filter(obj) }
        val option = view.showPopup(anchorNode = this, initialOption = selector.result.now.get())
        if (option != null) selector.select(option.reference())
    }
}