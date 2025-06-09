package ponticello.ui.registry

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.prompt.SimpleSearchableListView
import fxutils.setFixedWidth
import javafx.event.Event
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.input.DataFormat
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.MaterialDesignF
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import ponticello.model.obj.ScriptObject
import ponticello.model.obj.ScriptRegistry
import ponticello.sc.client.SuperColliderClient
import ponticello.ui.dock.ToolPaneState
import ponticello.ui.misc.CodePane
import ponticello.ui.registry.ObjectListView.DisplayMode
import reaktive.value.binding.map
import reaktive.value.now

class ScriptRegistryPane(registry: ScriptRegistry) : ObjectRegistryPane<ScriptObject>(registry) {
    override val title: String
        get() = "Scripts"

    override val icon: Ikon
        get() = MaterialDesignF.FILE_COG

    override val supportedModes: Set<DisplayMode>
        get() = setOf(DisplayMode.SubWindow, DisplayMode.DetailsPane)

    override fun defaultState(): ToolPaneState = ToolPaneState.docked(ToolPaneState.Side.RIGHT)

    override fun createNewObject(name: String, ev: Event?): ScriptObject? {
        val options = SCRIPT_TYPE_OPTIONS
        val type = SimpleSearchableListView(options, "Script type")
            .showPopup(ev, initialOption = ScriptObject.Type.REGULAR) ?: return null
        return ScriptObject.create(type, name)
    }

    override fun getItemContent(obj: ScriptObject): List<Node> {
        val typeSelector = SimpleSearchableListView(SCRIPT_TYPE_OPTIONS, "Script type")
            .selectorButton(obj.type).setFixedWidth(90.0)
        if (obj.type.now == ScriptObject.Type.BEFORE_BOOT) typeSelector.isDisable = true
        return listOf(typeSelector)
    }

    override fun detailWindowIcon(obj: ScriptObject): Ikon = Material2AL.CODE

    override fun dataFormat(obj: ScriptObject): DataFormat = ScriptObject.DATA_FORMAT

    override fun getActions(box: ObjectBox<ScriptObject>): List<ContextualizedAction> = actions.withContext(box.obj)

    override fun getContent(obj: ScriptObject, mode: DisplayMode): Parent =
        CodePane(obj.root, ownWindow = true)

    companion object {
        private val actions = collectActions<ScriptObject> {
            addAction("Execute script") {
                icon(MaterialDesignP.PLAY)
                enableWhen { script -> script.root.editor.result.map { code -> code.isValid } }
                executes { script ->
                    val client = script.context[SuperColliderClient]
                    script.executeContents(client)
                }
            }
        }

        private val SCRIPT_TYPE_OPTIONS = ScriptObject.Type.entries - ScriptObject.Type.BEFORE_BOOT
    }
}