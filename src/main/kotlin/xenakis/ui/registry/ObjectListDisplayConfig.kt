package xenakis.ui.registry

import fxutils.SubWindow
import fxutils.actions.ContextualizedAction
import javafx.geometry.Orientation
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.input.DataFormat
import javafx.scene.input.Dragboard
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignE
import reaktive.value.ReactiveString
import reaktive.value.reactiveValue
import xenakis.model.obj.ContextualObject
import xenakis.model.registry.NamedObject.Companion.NO_NAME

interface ObjectListDisplayConfig<O: ContextualObject> {
    val enableReordering: Boolean get() = false

    val enableAddObjectButton: Boolean get() = false

    val centerAddObjectButton: Boolean get() = false

    val buttonStyle: String get() = "medium-icon-button"

    val inlineOrientation: Orientation get() = Orientation.VERTICAL

    val supportedModes get() = setOf(ObjectListView.DisplayMode.Inline)

    val autoSelectNewObjects: Boolean get() = true

    fun detailWindowIcon(obj: O): Ikon = MaterialDesignE.EYE

    fun getItemContent(obj: O): List<Node> = emptyList()

    fun getContent(obj: O, mode: ObjectListView.DisplayMode): Parent? = null

    fun getActions(box: ObjectBox<O>): List<ContextualizedAction> = emptyList()

    fun configureDragboard(obj: O, dragboard: Dragboard) {}

    fun configureSubWindow(window: SubWindow) {}

    fun dataFormat(obj: O): DataFormat? = null

    fun getDefaultDisplayName(obj: O): ReactiveString = reactiveValue(NO_NAME)

    fun createNewObject(): O? = null

    fun onSelected(obj: O) {}

    fun onRemoved(obj: O) {}
}