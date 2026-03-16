package ponticello.ui.flow

import bundles.createBundle
import fxutils.*
import fxutils.actions.button
import fxutils.controls.SliderBar
import fxutils.undo.UndoManager
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import ponticello.model.flow.MixerFlow
import ponticello.sc.Rate
import ponticello.sc.editor.BusSelector
import ponticello.sc.view.ObjectSelectorControl
import ponticello.ui.registry.ListDisplayConfig
import ponticello.ui.registry.ObjectBox
import ponticello.ui.registry.ObjectListView
import reaktive.value.binding.equalTo
import reaktive.value.binding.flatMap
import reaktive.value.fx.asObservableValue
import reaktive.value.reactiveValue

class MixerFlowView private constructor(
    private val flow: MixerFlow,
    private val listConfig: MixerComponentListConfig,
) : VBox(), ListDisplayConfig<MixerFlow.MixerComponent> by listConfig {
    val componentsView = ObjectListView(flow.components, this, scrollable = false)

    init {
        val targetSelector = BusSelector()
        targetSelector.setFilter(rate = Rate.Audio, channels = null)
        targetSelector.syncWith(flow.targetBus)
        targetSelector.initialize(flow.context)
        val selectorControl = ObjectSelectorControl(targetSelector).widthAtLeast(100.0)

        val totalVolumeSlider = SliderBar(
            flow.masterVolume, reactiveValue("Master volume"),
            MixerFlow.VOLUME_SPEC.converter(unit = "db"),
            SliderBar.Style.AlwaysValue,
            undoManager = flow.context[UndoManager]
        ).alwaysHGrow()
        val addSourceBusBtn = MaterialDesignP.PLUS.button(
            "Add source bus", "medium-icon-button", listConfig::addSourceBus
        )
        children.addAll(
            HBox(5.0, label("Volume: "), totalVolumeSlider).centerChildren().pad(10.0),
            HBox(5.0, label("Target: "), selectorControl, addSourceBusBtn).centerChildren().pad(10.0),
            componentsView
        )
        styleClass("mixer-flow")
    }

    override fun getHeaderContent(obj: MixerFlow.MixerComponent): List<Node> {
        val selector = BusSelector()
        selector.setFilter(
            rate = reactiveValue(Rate.Audio),
            channels = flow.targetBus.flatMap { bus -> bus.get()?.channels ?: reactiveValue(0) }
        )
        selector.exclude { flow.usedBuses() }
        selector.syncWith(obj.sourceBus)
        selector.initialize(flow.context)
        val selectorControl = ObjectSelectorControl(selector, createBundle())
            .widthAtLeast(150.0)

        val converter = MixerFlow.VOLUME_SPEC.converter(unit = "db")
        val volumeSlider = SliderBar(
            obj.volume, "Volume (db)", converter, SliderBar.Style.AlwaysValue,
            undoManager = flow.context[UndoManager], updateActionDescription = "Update volume"
        )
        volumeSlider.disableProperty().bind(obj.state.equalTo(MixerFlow.MixerComponentMode.Mute).asObservableValue())

        val panKnob = listConfig.createPanKnob(obj.pan, 16.0)
        return listOf(selectorControl, volumeSlider, panKnob)
    }

    override fun getContent(obj: MixerFlow.MixerComponent, box: ObjectBox<MixerFlow.MixerComponent>): Parent = Region()

    companion object {
        fun create(flow: MixerFlow): MixerFlowView {
            val listConfig = MixerComponentListConfig()
            listConfig.setMixer(flow)
            return MixerFlowView(flow, listConfig)
        }
    }
}