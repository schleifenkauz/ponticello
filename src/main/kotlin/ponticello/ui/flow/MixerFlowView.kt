package ponticello.ui.flow

import bundles.createBundle
import fxutils.*
import fxutils.actions.ContextualizedAction
import fxutils.actions.button
import fxutils.actions.collectActions
import fxutils.controls.SliderBar
import fxutils.undo.UndoManager
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.input.DragEvent
import javafx.scene.input.Dragboard
import javafx.scene.input.MouseEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import org.kordamp.ikonli.materialdesign2.MaterialDesignA
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import ponticello.model.flow.MixerFlow
import ponticello.model.obj.BusObject
import ponticello.model.registry.BusRegistry
import ponticello.sc.Rate
import ponticello.sc.editor.BusSelector
import ponticello.sc.view.ObjectSelectorControl
import ponticello.ui.actions.ServerActions
import ponticello.ui.actions.undoable
import ponticello.ui.impl.getFrom
import ponticello.ui.registry.ObjectBox
import ponticello.ui.registry.ObjectListDisplayConfig
import ponticello.ui.registry.ObjectListView
import ponticello.ui.registry.SearchableBusListView
import reaktive.value.binding.flatMap
import reaktive.value.binding.not
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import reaktive.value.reactiveValue

class MixerFlowView(private val flow: MixerFlow) : VBox(), ObjectListDisplayConfig<MixerFlow.MixerComponent> {
    val componentsView = ObjectListView(flow.components, this)

    init {
        val totalVolumeSlider = SliderBar(
            flow.masterVolume, reactiveValue("Master volume"),
            MixerFlow.VOLUME_SPEC.converter(unit = "db"),
            SliderBar.Style.AlwaysValue,
            undoManager = flow.context[UndoManager]
        ).alwaysHGrow()
        val addSourceBusBtn = MaterialDesignP.PLUS.button("Add source bus", "medium-icon-button", ::addSourceBus)
        children.addAll(
            HBox(5.0, addSourceBusBtn, label("Volume: "), totalVolumeSlider)
                .pad(10.0).also { it.alignment = Pos.CENTER },
            componentsView
        )
        componentsView.setupDropArea(::canDrop, ::onDrop)
    }

    private fun addSourceBus(ev: MouseEvent) {
        val expectedChannels = flow.targetBus.now.get()?.channels?.now
        val bus = SearchableBusListView(
            flow.context[BusRegistry], "Select source bus",
            Rate.Audio, expectedChannels
        ).exclude { flow.usedBuses() }
            .showPopup(ev) ?: return
        flow.components.add(MixerFlow.MixerComponent.create(bus))
    }

    private fun canDrop(dragboard: Dragboard): Boolean {
        val bus = dragboard.getFrom(flow.context[BusRegistry], BusObject.DATA_FORMAT) ?: return false
        val expectedChannels = bus.channels.now == flow.targetBus.now.get()?.channels?.now
        return bus !in flow.usedBuses() && bus.rate == Rate.Audio && expectedChannels
    }

    private fun onDrop(ev: DragEvent) {
        val bus = ev.dragboard.getFrom(flow.context[BusRegistry], BusObject.DATA_FORMAT) ?: return
        flow.components.add(MixerFlow.MixerComponent.create(bus))
    }

    override fun getItemContent(obj: MixerFlow.MixerComponent): List<Node> {
        val selector = BusSelector()
        setupSourceBusSelector(selector, this@MixerFlowView.flow)
        selector.syncWith(obj.sourceBus)
        selector.initialize(flow.context)
        val selectorControl = ObjectSelectorControl(selector, createBundle())
            .widthAtLeast(150.0)
        val converter = MixerFlow.VOLUME_SPEC.converter(unit = "db")
        val volumeSlider = SliderBar(
            obj.volume, "Volume (db)", converter, SliderBar.Style.AlwaysValue,
            undoManager = flow.context[UndoManager], updateActionDescription = "Update volume"
        )
        volumeSlider.prefWidth = 150.0
        volumeSlider.disableProperty().bind(obj.mute.asObservableValue())
        return listOf(selectorControl, volumeSlider)
    }

    override fun getActions(box: ObjectBox<MixerFlow.MixerComponent>): List<ContextualizedAction> =
        actions.withContext(box.obj)

    companion object {
        private val actions = collectActions<MixerFlow.MixerComponent> {
            add(ServerActions.scopeBus) { f -> f.sourceBus }
            addAction("Toggle mute") {
                icon(MaterialDesignA.ALPHA_M_BOX)
                toggles(MixerFlow.MixerComponent::mute)
                undoable()
                enableWhen { comp -> comp.solo.not() }
            }
            addAction("Toggle solo") {
                icon(MaterialDesignA.ALPHA_S_BOX)
                toggles(MixerFlow.MixerComponent::solo)
                undoable()
                enableWhen { comp -> comp.mute.not() }
            }
        }

        fun setupSourceBusSelector(selector: BusSelector, flow: MixerFlow) {
            selector.setFilter(
                rate = reactiveValue(Rate.Audio),
                channels = flow.targetBus.flatMap { bus -> bus.get()?.channels ?: reactiveValue(0) }
            )
            selector.exclude { flow.usedBuses() }
        }
    }
}