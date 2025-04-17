package xenakis.ui.registry

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import javafx.scene.Parent
import javafx.scene.input.DataFormat
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.MaterialDesignS
import xenakis.model.obj.GlobalPatternObject
import xenakis.model.registry.ObjectRegistry

class GlobalPatternRegistryPane(
    registry: ObjectRegistry<GlobalPatternObject>
) : ObjectRegistryPane<GlobalPatternObject>(registry) {
    init {
        setup()
    }

    override fun createNewObject(name: String): GlobalPatternObject = GlobalPatternObject.create(name)

    override val supportedModes: Set<NamedObjectListView.DisplayMode>
        get() = NamedObjectListView.DisplayMode.all

    override val enableReordering: Boolean
        get() = true

    override fun getContent(obj: GlobalPatternObject): Parent = obj.patternCode.control

    override fun detailWindowIcon(obj: GlobalPatternObject): Ikon = Material2AL.CODE

    override fun getActions(box: ObjectBox<GlobalPatternObject>): List<ContextualizedAction> =
        actions.withContext(box.obj)

    override fun dataFormat(obj: GlobalPatternObject): DataFormat = GlobalPatternObject.DATA_FORMAT

    companion object {
        private val actions = collectActions<GlobalPatternObject> {
            addAction("Sync") {
                icon(MaterialDesignS.SYNC)
                shortcut("Ctrl+U")
                executes { obj -> obj.sync() }
            }
        }
    }
}