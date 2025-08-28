package ponticello.ui.dock

import fxutils.actions.ContextualizedAction
import fxutils.actions.makeButton
import fxutils.setRoot
import javafx.geometry.Orientation
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.Control
import javafx.scene.layout.Region
import ponticello.model.registry.ObjectList
import ponticello.ui.registry.ListDisplayConfig
import ponticello.ui.registry.ObjectBox
import ponticello.ui.registry.ObjectListView

abstract class ReorderableActionBar<O : Any>(
    private val objects: ObjectList<O>, private val style: List<String>,
) : ListDisplayConfig<O>, Control() {
    constructor(actions: ObjectList<O>, style: String) : this(actions, listOf(style))
    constructor(actions: ObjectList<O>, vararg style: String) : this(actions, style.asList())

    lateinit var layout: ObjectListView<O>
        private set

    protected fun setup() {
        layout = ObjectListView(objects, this, scrollable = false)
        setRoot(layout)
    }

    override val enableSelection: Boolean
        get() = false

    override val boxStyle: Array<String>
        get() = arrayOf("action-box")

    override val listStyle: Array<String>
        get() = arrayOf("action-bar")

    override val inlineOrientation: Orientation
        get() = Orientation.VERTICAL

    override val supportedModes: Set<ObjectListView.DisplayMode>
        get() = setOf(ObjectListView.DisplayMode.Inline(collapsable = false))

    override val hideWhileDragging: Boolean
        get() = true

    override val showDragHandle: Boolean
        get() = false

    override fun canDelete(obj: O): Boolean = false

    override fun getDragTarget(box: ObjectBox<O>): Node = box.content ?: Region()

    protected abstract fun getAction(obj: O): ContextualizedAction

    override fun getContent(obj: O, mode: ObjectListView.DisplayMode): Parent? {
        val action = getAction(obj)
        return action.makeButton(style)
    }

    override fun boxLayout(obj: O, header: Region, content: Node?): Node = content ?: Region()

    companion object {
        operator fun invoke(style: String, actions: ObjectList<ContextualizedAction>) =
            object : ReorderableActionBar<ContextualizedAction>(actions, style) {
                override fun getAction(obj: ContextualizedAction): ContextualizedAction = obj
            }
    }
}