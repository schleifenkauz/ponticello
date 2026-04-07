package ponticello.sc.view

import bundles.Bundle
import bundles.createBundle
import fxutils.actions.action
import fxutils.centerChildren
import fxutils.drag.DropHandler
import fxutils.drag.setupDropArea
import fxutils.prompt.PromptPlacement
import fxutils.prompt.SimpleSelectorPrompt
import hextant.context.compoundEdit
import hextant.core.view.SimpleChoiceEditorControl
import javafx.application.Platform
import javafx.scene.control.Label
import javafx.scene.control.Tooltip
import javafx.scene.input.*
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import org.kordamp.ikonli.materialdesign2.MaterialDesignE
import ponticello.model.obj.NamedObject
import ponticello.model.registry.NamedObjectList
import ponticello.model.registry.ObjectList
import ponticello.model.registry.ObjectReference
import ponticello.model.registry.reference
import ponticello.sc.editor.ObjectSelector
import ponticello.ui.impl.getFrom
import ponticello.ui.registry.RegistrySelectorPrompt
import reaktive.value.now
import reaktive.value.reactiveValue

class ObjectSelectorControl<O : NamedObject>(
    private val selector: ObjectSelector<O>, arguments: Bundle = createBundle(),
    private val displayType: Boolean = false
) : SimpleChoiceEditorControl<ObjectReference<O>>(selector, arguments), DropHandler {
    init {
        root.setupDropArea(this)
        selectorButton.setOnDragDetected(::dragDetected)
        selectorButton.tooltip = Tooltip(
            if (selector.canViewSelected) "Right click to view selected ${selector.objectType}."
            else selector.objectType
        )
        if (selector.canViewSelected) {
            selectorButton.addEventHandler(MouseEvent.MOUSE_CLICKED) { ev ->
                if (ev.button == MouseButton.SECONDARY) {
                    selector.viewSelected()
                    ev.consume()
                }
            }
        }
    }

    override fun createDefaultRoot(): Region {
        if (!displayType) return super.createDefaultRoot()
        val label = Label("#")
        label.setOnMouseClicked { ev ->
            receiveFocus()
            ev.consume()
        }
        return HBox(2.0, label, selectorButton).centerChildren()
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
        return dragboard.getFrom(selector.getOptions(), format)
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

    public override fun showChoicePopup() = Platform.runLater {
        val registry = selector.getOptions()
        val view =
            if (registry is NamedObjectList) SimpleRegistrySelectorPrompt(registry)
            else SimpleListSelectorPrompt(registry)
        view.setFilter { obj -> selector.filter(obj) }
        val objectType =
            if (registry is ObjectList) registry.objectType
            else registry.javaClass.simpleName
        context.compoundEdit("Select $objectType") {
            val option = view.showPopup(anchorNode = this, initialOption = selector.result.now.get())
            if (option != null) selector.select(option.reference())
        }
    }

    private inner class SimpleRegistrySelectorPrompt(
        registry: NamedObjectList<O>
    ) : RegistrySelectorPrompt<O>(registry, "Select ${registry.objectType}") {
        override val canCreateItem: Boolean get() = selector.canCreateItem

        override fun createObject(name: String): O? =
            selector.createNewObject(name, PromptPlacement.RelativeTo(this@ObjectSelectorControl))

        override fun displayText(option: O): String = selector.toString(option).now
    }

    private inner class SimpleListSelectorPrompt(registry: List<O>) : SimpleSelectorPrompt<O>(registry, "Select") {
        override fun displayText(option: O): String = selector.toString(option).now
    }

    companion object {
        private val viewSelectedAction = action<ObjectSelector<*>>("View") {
            icon(MaterialDesignE.EYE)
            enableWhen { selector ->
                if (selector.canViewSelected) selector.isResolved
                else reactiveValue(false)
            }
            executes { selector -> selector.viewSelected() }
        }
    }
}