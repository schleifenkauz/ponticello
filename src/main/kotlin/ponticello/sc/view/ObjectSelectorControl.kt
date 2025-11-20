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
import ponticello.ui.impl.getFrom
import ponticello.ui.registry.RegistrySelectorPrompt
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
        val db = startDragAndDrop(TransferMode.MOVE, TransferMode.LINK)
        db.setContent(mapOf(format to selector.result.now))
    }

    private fun extractObject(dragboard: Dragboard): O? {
        val format = selector.dataFormat() ?: return null
        return dragboard.getFrom(selector.getList(), format)
    }

    override fun acceptedTransferModes(event: DragEvent): Array<out TransferMode> {
        val obj = extractObject(event.dragboard)
        return if (obj != null && obj != selector.result.now.get()) arrayOf(TransferMode.LINK) else emptyArray()
    }

    override fun drop(event: DragEvent): Boolean {
        val db = event.dragboard
        val obj = extractObject(db) ?: return false
        selector.select(obj.reference())
        return true
    }

    public override fun showChoicePopup() {
        val registry = selector.getList()
        val view = object : RegistrySelectorPrompt<O>(registry, "Select ${registry.objectType}") {
            override val canCreateItem: Boolean get() = true

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