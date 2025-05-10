package ponticello.ui.registry

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.setFixedWidth
import javafx.event.Event
import javafx.scene.Node
import javafx.scene.Parent
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.material2.Material2AL
import ponticello.model.obj.ProcessDefObject
import ponticello.model.registry.GlobalDefinitionLibrary
import ponticello.model.registry.ProcessDefRegistry
import ponticello.ui.impl.colorPicker
import ponticello.ui.registry.ObjectListView.DisplayMode

class ProcessDefRegistryPane(
    registry: ProcessDefRegistry,
) : ParameterizedObjectDefRegistryPane<ProcessDefObject>(registry, GlobalDefinitionLibrary.processDefs) {
    override val supportedModes: Set<DisplayMode> get() = setOf(DisplayMode.DetailsPane, DisplayMode.SubWindow)

    init {
        setup()
    }

    override fun detailWindowIcon(obj: ProcessDefObject): Ikon = Material2AL.CODE

    override fun getItemContent(obj: ProcessDefObject): List<Node> = listOf(colorPicker(obj.color).setFixedWidth(30.0))

    override fun createNewObject(name: String, ev: Event?): ProcessDefObject = ProcessDefObject.newEmpty(name)

    override fun getContent(obj: ProcessDefObject, mode: DisplayMode): Parent {
        val enableActions = mode == DisplayMode.SubWindow
        return ProcessDefObjectPane(obj, enableActions)
    }

    override fun getActions(box: ObjectBox<ProcessDefObject>): List<ContextualizedAction> = actions.withContext(box)

    companion object {
        private val actions = collectActions<ObjectBox<ProcessDefObject>> {
        }
    }
}