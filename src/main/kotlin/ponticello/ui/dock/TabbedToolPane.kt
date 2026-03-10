package ponticello.ui.dock

import fxutils.button
import fxutils.styleClass
import javafx.css.PseudoClass
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.ScrollPane
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import ponticello.model.obj.NamedObject
import ponticello.model.registry.ObjectList
import ponticello.model.registry.ObjectReference
import ponticello.model.registry.reference

abstract class TabbedToolPane<T : NamedObject>(protected val items: ObjectList<T>) : ToolPane(),
    ObjectList.Listener<T> {
    private val cachedViews = mutableMapOf<T, Parent>()
    private val itemBoxes = mutableMapOf<T, Node>()

    private var selectedObject: ObjectReference<T> = ObjectReference.none()

    override var content: Parent = Region()
        set(value) {
            if (children.size < 2) children.add(value)
            else children[1] = value
            field = value
        }

    protected val itemsLayout = HBox(3.0)

    override val headerContent: Node = ScrollPane(itemsLayout)

    protected fun cachedViews(): Collection<Node> = cachedViews.values

    override fun defaultState(): ToolPaneState = TabbedToolPaneState.default()

    override fun doSetup() {
        super.doSetup()
        items.addListener(this)
        val state = initialState
        if (state is TabbedToolPaneState) {
            val selected = state.selected.get() as T?
            if (selected != null) select(selected)
            else if (items.isNotEmpty()) select(items.first())
        }
        addEventFilter(MouseEvent.MOUSE_CLICKED) { requestFocus() }
    }

    override fun saveState(dest: ToolPaneState) {
        super.saveState(dest)
        if (dest is TabbedToolPaneState && isSetup) {
            dest.selected = selectedObject
        }
    }

    override fun added(obj: T, idx: Int) {
        val box = createItemBox(obj) styleClass "tabbed-pane-item"
        box.setOnMouseClicked { ev ->
            if (ev.button == MouseButton.PRIMARY) {
                select(obj)
                ev.consume()
            }
        }
        itemBoxes[obj] = box
        itemsLayout.children.add(idx, box)
    }

    override fun removed(obj: T, idx: Int) {
        itemBoxes.remove(obj)
        itemsLayout.children.removeAt(idx)
        cachedViews.remove(obj) //TODO really?
        if (selectedObject.get() == obj) {
            select(items.getOrNull(idx) ?: items.lastOrNull())
        }
    }

    override fun moved(obj: T, idx: Int) {
        val box = itemsLayout.children.removeAt(idx)
        itemsLayout.children.add(idx, box)
    }

    fun select(obj: T?) {
        content = if (obj != null) content(obj) else Region()
        selected(obj)
        val previouslySelectedBox = itemBoxes[selectedObject.get()]
        previouslySelectedBox?.pseudoClassStateChanged(SELECTED, false)
        val selectedBox = itemBoxes[obj]
        selectedBox?.pseudoClassStateChanged(SELECTED, true)
        selectedObject = obj?.reference() ?: ObjectReference.none()
    }

    protected fun getItemBox(obj: T): Node? = itemBoxes[obj]

    protected fun content(obj: T): Parent = cachedViews.getOrPut(obj) { getContent(obj) }

    protected abstract fun getContent(obj: T): Parent

    protected open fun selected(obj: T?) {}

    protected open fun createItemBox(obj: T): Node = button(obj.name)

    companion object {
        private val SELECTED = PseudoClass.getPseudoClass("selected")
    }
}