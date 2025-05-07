package xenakis.ui.flow

import bundles.createBundle
import fxutils.actions.Action
import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.controls.SliderBar
import fxutils.setFixedWidth
import fxutils.setRoot
import javafx.scene.Node
import javafx.scene.control.Control
import org.kordamp.ikonli.materialdesign2.MaterialDesignA
import reaktive.value.binding.flatMap
import reaktive.value.binding.not
import reaktive.value.fx.asObservableValue
import reaktive.value.reactiveValue
import xenakis.model.flow.MixerFlow
import xenakis.sc.Rate
import xenakis.sc.editor.BusSelector
import xenakis.sc.view.ObjectSelectorControl
import xenakis.ui.actions.ServerActions
import xenakis.ui.registry.ObjectBox
import xenakis.ui.registry.ObjectListDisplayConfig
import xenakis.ui.registry.ObjectListView

class MixerFlowView(private val flow: MixerFlow) : Control(), ObjectListDisplayConfig<MixerFlow.MixerComponent> {
    val componentsView = ObjectListView(flow.components, this)

    init {
        setRoot(componentsView)
    }

    override fun getItemContent(obj: MixerFlow.MixerComponent): List<Node> {
        val selector = BusSelector()
        selector.setFilter(
            rate = reactiveValue(Rate.Audio),
            channels = flow.targetBus.flatMap { bus -> bus.get()?.channels ?: reactiveValue(0) }
        )
        selector.syncWith(obj.sourceBus)
        selector.initialize(flow.context)
        val selectorControl = ObjectSelectorControl(selector, createBundle())
            .setFixedWidth(100.0)
        val name = reactiveValue("Volume")
        val converter = MixerFlow.VOLUME_SPEC.converter()
        val volumeSlider = SliderBar(obj.volume, name, converter, SliderBar.Style.AlwaysValue)
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
                applicableWhen { comp -> comp.solo.not() }
                ifNotApplicable(Action.IfNotApplicable.Disable)
            }
            addAction("Toggle solo") {
                icon(MaterialDesignA.ALPHA_S_BOX)
                toggles(MixerFlow.MixerComponent::solo)
                applicableWhen { comp -> comp.mute.not() }
                ifNotApplicable(Action.IfNotApplicable.Disable)
            }
        }
    }
}