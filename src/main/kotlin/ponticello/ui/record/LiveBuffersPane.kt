package ponticello.ui.record

import fxutils.actions.action
import fxutils.actions.makeButton
import fxutils.centerChildren
import fxutils.label
import fxutils.prompt.YesNoPrompt
import fxutils.styleClass
import javafx.css.PseudoClass
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import org.kordamp.ikonli.materialdesign2.MaterialDesignM
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import ponticello.impl.rangeTo
import ponticello.impl.toDecimal
import ponticello.impl.zero
import ponticello.model.obj.LiveBufferReference
import ponticello.model.project.LIVE_BUFFERS
import ponticello.model.project.PonticelloProject
import ponticello.model.project.get
import ponticello.model.record.LiveBufferObject
import ponticello.model.record.LiveBufferRegistry
import ponticello.model.registry.ObjectList
import ponticello.model.registry.ObjectReference
import ponticello.model.registry.reference
import ponticello.ui.dock.LiveBufferRegistryPaneState
import ponticello.ui.dock.Side
import ponticello.ui.dock.ToolPane
import ponticello.ui.dock.ToolPaneState

class LiveBuffersPane(
    private val buffers: LiveBufferRegistry
) : ToolPane(), ObjectList.Listener<LiveBufferObject> {
    override val type: Type get() = LiveBuffersPane

    private val cachedViews = mutableMapOf<LiveBufferObject, LiveAudioBufferView>()
    private val itemBoxes = mutableMapOf<LiveBufferObject, HBox>()

    private var selectedObject: LiveBufferReference = ObjectReference.none()

    private val itemsLayout = HBox(3.0)

    override var content: Parent = Region()
        set(value) {
            if (children.size < 2) children.add(value)
            else children[1] = value
            field = value
        }

    private val addBufferButton = addItemButton.withContext(this).makeButton("medium-icon-button")

    override val headerContent: Node
        get() = HBox(5.0, itemsLayout, addBufferButton).centerChildren()

    override fun defaultState(): ToolPaneState = LiveBufferRegistryPaneState.default()

    override fun doSetup() {
        super.doSetup()
        buffers.addListener(this)
        val state = initialState
        if (state is LiveBufferRegistryPaneState) {
            val selected = state.selected.get()
            if (selected != null) select(selected)
            else if (buffers.isNotEmpty()) select(buffers.first())
        }
    }

    override fun saveState(dest: ToolPaneState) {
        super.saveState(dest)
        if (dest is LiveBufferRegistryPaneState && isSetup) {
            dest.selected = selectedObject
        }
    }

    override fun added(obj: LiveBufferObject, idx: Int) {
        val box = createItemBox(obj)
        itemBoxes[obj] = box
        itemsLayout.children.add(idx, box)
    }

    override fun removed(obj: LiveBufferObject, idx: Int) {
        itemBoxes.remove(obj)
        itemsLayout.children.removeAt(idx)
        if (selectedObject.get() == obj) {
            select(buffers.getOrNull(idx) ?: buffers.lastOrNull())
        }
    }

    override fun moved(obj: LiveBufferObject, idx: Int) {
    }

    private fun createItemBox(obj: LiveBufferObject): HBox {
        val label = label(obj.name)
        val deleteBtn = deleteBufferAction.withContext(obj).makeButton("small-icon-button")
        val toggleBtn = LiveBufferObject.toggleEnabledAction.withContext(obj).makeButton("medium-icon-button")
        val box = HBox(toggleBtn, label, deleteBtn) styleClass "live-buffer-item"
        label.setOnMouseClicked {
            select(obj)
        }
        return box
    }

    private fun select(obj: LiveBufferObject?) {
        content = if (obj != null) getLiveBufferView(obj) else Region()
        val previouslySelectedBox = itemBoxes[selectedObject.get()]
        previouslySelectedBox?.pseudoClassStateChanged(SELECTED, false)
        val selectedBox = itemBoxes[obj]
        selectedBox?.pseudoClassStateChanged(SELECTED, true)
        selectedObject = obj?.reference() ?: ObjectReference.none()
    }

    private fun getLiveBufferView(obj: LiveBufferObject) =
        cachedViews.getOrPut(obj) {
            val view = LiveAudioBufferView(obj, initialDisplayRange = zero..10.toDecimal())
            setVgrow(view, Priority.ALWAYS)
            view
        }

    companion object : Type(uid = 18, "LiveBuffers") {
        override val defaultSide: Side
            get() = Side.BOTTOM

        override val icon: Ikon get() = MaterialDesignM.MICROPHONE

        override fun createToolPane(project: PonticelloProject): ToolPane = LiveBuffersPane(project[LIVE_BUFFERS])

        private val SELECTED = PseudoClass.getPseudoClass("selected")

        private val deleteBufferAction = action<LiveBufferObject>("Delete buffer") {
            shortcut("Ctrl+Delete")
            icon(MaterialDesignC.CLOSE)
            executes { buffer, ev ->
                val delete = YesNoPrompt("Delete buffer?").showDialog(ev)
                if (delete == true) {
                    buffer.registry.remove(buffer)
                }
            }
        }

        private val addItemButton = action<LiveBuffersPane>("Add buffer") {
            icon(MaterialDesignP.PLUS_CIRCLE)
            executes { pane, ev ->
                val buffer = NewLiveBufferPrompt(context).showDialog(ev) ?: return@executes
                pane.buffers.add(buffer)
                pane.select(buffer)
            }
        }
    }
}