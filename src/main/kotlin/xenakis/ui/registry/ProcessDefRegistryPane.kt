package xenakis.ui.registry

import fxutils.SubWindow
import fxutils.setFixedWidth
import hextant.fx.initHextantScene
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.paint.Color
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.material2.Material2AL
import reaktive.value.binding.map
import xenakis.model.obj.ProcessDefObject
import xenakis.model.registry.ProcessDefRegistry
import xenakis.ui.impl.colorPicker
import xenakis.ui.registry.NamedObjectListView.ContentDisplay

class ProcessDefRegistryPane(
    registry: ProcessDefRegistry,
) : SuperColliderObjectRegistryPane<ProcessDefObject>(registry) {
    override val supportedModes: Set<ContentDisplay> get() = setOf(ContentDisplay.DetailsPane, ContentDisplay.SubWindow)

    override fun detailWindowIcon(obj: ProcessDefObject): Ikon = Material2AL.CODE

    override fun getItemContent(obj: ProcessDefObject): List<Node> {
        val colorPicker = colorPicker(obj.color)
        colorPicker.setFixedWidth(30.0)
        return listOf(colorPicker)
    }

    override fun addObject(name: String): ProcessDefObject {
        val processDef = ProcessDefObject.newEmpty(name)
        registry.add(processDef)
        return processDef
    }

    override fun getContent(obj: ProcessDefObject): Parent? {
        val title = obj.name.map { n -> "ProcessDef $n" }
        return ParameterizedObjectDefPane(registry.context, title, obj.parameters, obj.processCode, obj::sync)
    }

    override fun configureSubWindow(window: SubWindow) {
        window.scene.initHextantScene(registry.context, applyStyle = false)
        window.scene.fill = Color.BLACK
    }
}