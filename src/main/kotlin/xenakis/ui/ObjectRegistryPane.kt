package xenakis.ui

import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.SplitPane
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import xenakis.model.NamedObject
import xenakis.model.ObjectRegistry
import xenakis.model.RenamableObject

abstract class ObjectRegistryPane<O : NamedObject>(
    private val registry: ObjectRegistry<O>
) : VBox(), ObjectRegistry.View<O> {
    private val collapseExpandBtn = Icon.Expand.button { if (boxes in children) collapse() else expand() }
    protected val boxes = VBox()
    private val header = createHeader()

    init {
        styleClass.add("tool-pane")
        children.add(header)
        expand()
        SplitPane.setResizableWithParent(this, false)
    }

    private fun createHeader(): HBox {
        val type = registry.objectType
        val label = Label(plural(type)).styleClass("tool-pane-heading")
        val space = infiniteSpace()
        val addBtn = Icon.Add.button(action = "Add $type") {
            addObject()
            expand()
        }
        val reloadBtn = Icon.Repeat.button(action = "Sync ${type}s") { reload() }
        return HBox(label, space, addBtn, reloadBtn, collapseExpandBtn).styleClass("tool-pane-header")
    }

    fun collapse() {
        children.remove(boxes)
        collapseExpandBtn.rotate = 0.0
        collapseExpandBtn.tooltip = Tooltip("Collapse")
        /*maxHeight = header.height*/
    }

    fun expand() {
        if (boxes !in children) children.add(boxes)
        collapseExpandBtn.rotate = 180.0
        collapseExpandBtn.tooltip = Tooltip("Expand")
        /*maxHeight = USE_PREF_SIZE*/
    }

    protected abstract fun reload()

    protected open fun addObject() = showNamePrompt(registry) { name -> addObject(name) }

    protected abstract fun addObject(name: String)

    protected open fun canDelete(obj: O): Boolean = true

    protected open fun ObjectBox<O>.configureObjectBox() {}

    override fun added(obj: O, idx: Int) {
        val box = ObjectBox(this, obj)
        box.configureObjectBox()
        boxes.children.add(idx, box)
    }

    override fun removed(obj: O, idx: Int) {
        boxes.children.removeAt(idx)
    }

    @Suppress("UNCHECKED_CAST")
    fun box(idx: Int): ObjectBox<O> = boxes.children[idx] as ObjectBox<O>

    class ObjectBox<O : NamedObject>(private val pane: ObjectRegistryPane<O>, val obj: O) : HBox() {
        val actions = HBox().centerChildrenVertically()

        private val extraControls = HBox(5.0).centerChildrenVertically()

        init {
            styleClass("object-box")
            val nameDisplay = if (obj is RenamableObject) NameControl(obj) else label(obj.name) styleClass "name"
            children.addAll(nameDisplay, extraControls, infiniteSpace(), actions)
            addAction(Icon.Delete, "Remove object") { pane.registry.remove(obj) }.isDisable = !pane.canDelete(obj)
        }

        fun addAction(icon: Icon, description: String, action: () -> Unit): Button {
            val button = icon.button(action = description) { action() }
            actions.children.add(0, button)
            return button
        }

        fun addExtraControl(vararg node: Node) {
            extraControls.children.addAll(*node)
        }
    }
}