package xenakis.ui.registry

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.setFixedWidth
import javafx.scene.Node
import javafx.scene.Parent
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.MaterialDesignE
import xenakis.model.obj.ProcessDefObject
import xenakis.model.registry.GlobalDefinitionLibrary
import xenakis.model.registry.ProcessDefRegistry
import xenakis.ui.impl.colorPicker
import xenakis.ui.registry.NamedObjectListView.DisplayMode

class ProcessDefRegistryPane(
    registry: ProcessDefRegistry,
) : ParameterizedObjectDefRegistryPane<ProcessDefObject>(registry, GlobalDefinitionLibrary.processDefs) {
    override val supportedModes: Set<DisplayMode> get() = setOf(DisplayMode.DetailsPane, DisplayMode.SubWindow)

    init {
        setup()
    }

    override fun detailWindowIcon(obj: ProcessDefObject): Ikon = Material2AL.CODE

    override fun getItemContent(obj: ProcessDefObject): List<Node> = listOf(colorPicker(obj.color).setFixedWidth(30.0))

    override fun createNewObject(name: String): ProcessDefObject = ProcessDefObject.newEmpty(name)

    override fun getContent(obj: ProcessDefObject): Parent = ProcessDefObjectPane(obj)

    override fun getActions(box: ObjectBox<ProcessDefObject>): List<ContextualizedAction> = actions.withContext(box)

    companion object {
        private val actions = collectActions<ObjectBox<ProcessDefObject>> {
            addAction("Save to global library") {
                icon(MaterialDesignE.EXPORT_VARIANT)
                executes { box ->
                    val def = box.obj
                    def.context[GlobalDefinitionLibrary.processDefs].saveToGlobalLib(def, anchorNode = box)
                }
            }
        }
    }
}