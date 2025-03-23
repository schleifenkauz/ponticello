package xenakis.ui.registry

import fxutils.SubWindow
import fxutils.actions.ContextualizedAction
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.input.DataFormat
import javafx.scene.input.Dragboard
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignE
import reaktive.value.ReactiveString
import reaktive.value.reactiveValue
import xenakis.model.registry.NamedObject
import xenakis.model.registry.NamedObject.Companion.NO_NAME

interface ObjectBoxConfig<O : NamedObject> {
    val enableReordering: Boolean get() = false

    val buttonStyle: String get() = "medium-icon-button"

    fun detailWindowIcon(obj: O): Ikon = MaterialDesignE.EYE

    val supportedModes get() = setOf(NamedObjectListView.ContentDisplay.Inline)

    fun getItemContent(obj: O): List<Node> = emptyList()

    fun getContent(obj: O): Parent? = null

    fun getActions(obj: O): List<ContextualizedAction> = emptyList()

    fun configureDragboard(obj: O, dragboard: Dragboard) {}

    fun configureSubWindow(window: SubWindow) {}

    fun dataFormat(obj: O): DataFormat? = null

    fun getDefaultDisplayName(obj: O): ReactiveString = reactiveValue(NO_NAME)

    fun onSelected(obj: O) {}

    fun onRemoved(obj: O) {}
}