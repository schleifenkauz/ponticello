package ponticello.ui.registry

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.prompt.SimpleSelectorPrompt
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
import ponticello.model.project.PonticelloProject
import ponticello.model.project.SCRIPTS
import ponticello.model.project.scripts
import ponticello.sc.client.SuperColliderClient
import ponticello.ui.dock.ListToolPaneState
import ponticello.ui.dock.Side
import ponticello.ui.dock.ToolPane
import ponticello.ui.dock.ToolPaneState
import ponticello.ui.misc.CodePane
import ponticello.ui.registry.ObjectListView.DisplayMode
import reaktive.value.binding.map
import reaktive.value.now

class ScriptRegistryPane(registry: ScriptRegistry) : ObjectRegistryPane<ScriptObject>(registry, SCRIPTS.serializer) {
    override val type: Type
        get() = ScriptRegistryPane

    override val supportedModes: Set<DisplayMode>
        get() = setOf(DisplayMode.SubWindow, DisplayMode.DetailsPane)

    override val canDuplicate: Boolean
        get() = true

    override fun defaultState(): ToolPaneState = ListToolPaneState.docked

    override fun createNewObject(name: String, ev: Event?): ScriptObject? {
        val options = SCRIPT_TYPE_OPTIONS
        val type = SimpleSelectorPrompt(options, "Script type")
            .showPopup(ev, initialOption = ScriptObject.Type.REGULAR) ?: return null
        return ScriptObject.create(type, name)
    }

    override fun getHeaderContent(obj: ScriptObject): List<Node> {
        val typeSelector = SimpleSelectorPrompt(SCRIPT_TYPE_OPTIONS, "Script type")
            .selectorButton(obj.type).setFixedWidth(90.0)
        if (obj.type.now == ScriptObject.Type.BEFORE_BOOT) typeSelector.isDisable = true
        return listOf(typeSelector)
    }

    override fun detailWindowIcon(obj: ScriptObject): Ikon = Material2AL.CODE

    override val dataFormat: DataFormat
        get() = ScriptObject.DATA_FORMAT

    override fun getActions(box: ObjectBox<ScriptObject>): List<ContextualizedAction> = actions.withContext(box.obj)

    override fun getContent(obj: ScriptObject, box: ObjectBox<ScriptObject>): Parent =
        CodePane(obj.root, ownWindow = true)

    companion object : Type(11, "Scripts") {
        override val icon: Ikon
            get() = MaterialDesignF.FILE_COG

        override val shortcut: String
            get() = "F6"

        override val defaultSide: Side
            get() = Side.RIGHT

        override fun createToolPane(project: PonticelloProject): ToolPane = ScriptRegistryPane(project.scripts)

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