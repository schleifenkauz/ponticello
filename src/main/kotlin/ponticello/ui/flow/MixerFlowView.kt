package ponticello.ui.flow

import bundles.createBundle
import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.controls.SliderBar
import fxutils.setRoot
import fxutils.undo.UndoManager
import fxutils.widthAtLeast
import javafx.scene.Node
import javafx.scene.control.Control
import org.kordamp.ikonli.materialdesign2.MaterialDesignA
import ponticello.model.flow.MixerFlow
import ponticello.sc.Rate
import ponticello.sc.editor.BusSelector
import ponticello.sc.view.ObjectSelectorControl
import ponticello.ui.actions.ServerActions
import ponticello.ui.actions.undoable
import ponticello.ui.registry.ObjectBox
import ponticello.ui.registry.ObjectListDisplayConfig
import ponticello.ui.registry.ObjectListView
import reaktive.value.binding.flatMap
import reaktive.value.binding.not
import reaktive.value.fx.asObservableValue
import reaktive.value.reactiveValue

class MixerFlowView(private val flow: MixerFlow) : Control(), ObjectListDisplayConfig<MixerFlow.MixerComponent> {
    val componentsView = ObjectListView(flow.components, this)

    init {
        setRoot(componentsView)
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