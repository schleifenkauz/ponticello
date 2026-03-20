package ponticello.ui.flow

import fxutils.*
import fxutils.prompt.PromptPlacement
import fxutils.prompt.SimpleSelectorPrompt
import javafx.scene.Node
import org.kordamp.ikonli.javafx.FontIcon
import org.kordamp.ikonli.materialdesign2.MaterialDesignA
import ponticello.model.flow.VSTPluginFlow
import ponticello.model.flow.VSTPluginParameterMapping
import ponticello.model.registry.ObjectList
import ponticello.model.server.BusRegistry
import ponticello.sc.Rate
import ponticello.sc.editor.BusSelector
import ponticello.sc.view.ObjectSelectorControl
import ponticello.ui.dock.ListToolPane
import ponticello.ui.registry.BusSelectorPrompt
import ponticello.ui.registry.ObjectBox
import reaktive.value.ReactiveValue
import reaktive.value.reactiveValue
import reaktive.value.toggle

class VSTPluginParameterMappingsPane(
    private val flow: VSTPluginFlow,
) : ListToolPane<VSTPluginParameterMapping>(flow.parameterMappings) {
    override val title: ReactiveValue<String>
        get() = reactiveValue("Parameter mappings")

    init {
        setup()
    }

    override fun getHeaderContent(obj: VSTPluginParameterMapping): List<Node> = buildList {
        add(label(obj.name).setFixedWidth(150.0))
        val arrow = FontIcon(MaterialDesignA.ARROW_LEFT_RIGHT) styleClass "parameter-mapping-arrow"
        arrow.userData = arrow.bindPseudoClassState("active", obj.active)
        arrow.setOnMouseClicked { ev ->
            obj.active.toggle()
            ev.consume()
        }

        add(arrow)
        add(hspace(5.0))
        val busSelector = BusSelector()
        busSelector.setFilter(rate = Rate.Control, channels = 1)
        busSelector.syncWith(obj.controlBus)
        busSelector.initialize(obj.context)
        val selectorControl = ObjectSelectorControl(busSelector)
        add(selectorControl)
    }

    override fun getDragTarget(box: ObjectBox<VSTPluginParameterMapping>): Node = box

    override fun createNewObject(
        promptPlacement: PromptPlacement,
        list: ObjectList<VSTPluginParameterMapping>
    ): VSTPluginParameterMapping? {
        val options = flow.automatableParameters - flow.parameterMappings.mapTo(mutableSetOf()) { mapping -> mapping.name }
        val name = SimpleSelectorPrompt(options, "Select parameter")
            .showPopup(promptPlacement) ?: return null
        val bus = BusSelectorPrompt(list.context[BusRegistry], "Select control bus", Rate.Control, 1)
            .showPopup(promptPlacement) ?: return null
        return VSTPluginParameterMapping.create(name, bus)
    }
}