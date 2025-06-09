package ponticello.ui.registry

import fxutils.SubWindow
import fxutils.actions.ContextualizedAction
import javafx.event.Event
import javafx.geometry.Orientation
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.input.DataFormat
import javafx.scene.input.Dragboard
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignE
import ponticello.model.obj.ContextualObject
import ponticello.model.registry.NamedObject.Companion.NO_NAME
import reaktive.value.ReactiveString
import reaktive.value.reactiveValue

interface ObjectListDisplayConfig<O : ContextualObject> {
    val enableReordering: Boolean get() = false

    val enableAddObjectButton: Boolean get() = false

    val centerAddObjectButton: Boolean get() = false

    val buttonStyle: String get() = "medium-icon-button"

    val inlineOrientation: Orientation get() = Orientation.VERTICAL

    val supportedModes get() = setOf(ObjectListView.DisplayMode.Inline)

    val autoSelectNewObjects: Boolean get() = true

    val addSpaceBeforeActionBar: Boolean get() = true

    fun detailWindowIcon(obj: O): Ikon = MaterialDesignE.EYE

    fun getItemContent(obj: O): List<Node> = emptyList()

    fun getContent(obj: O, mode: ObjectListView.DisplayMode): Parent? = null

    fun getActions(box: ObjectBox<O>): List<ContextualizedAction> = emptyList()

    fun configureDragboard(obj: O, dragboard: Dragboard) {}

    fun configureSubWindow(window: SubWindow, obj: O) {}

    fun dataFormat(obj: O): DataFormat? = null

    fun getDefaultDisplayName(obj: O): ReactiveString = reactiveValue(NO_NAME)

    fun createNewObject(ev: Event?): O? = null

    fun onSelected(obj: O) {}

    fun onRemoved(obj: O) {}
}