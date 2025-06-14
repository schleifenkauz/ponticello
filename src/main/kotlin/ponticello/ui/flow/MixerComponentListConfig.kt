package ponticello.ui.flow

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.undo.UndoManager
import javafx.scene.input.DragEvent
import javafx.scene.input.Dragboard
import javafx.scene.input.MouseEvent
import javafx.scene.input.TransferMode
import javafx.scene.paint.Color
import org.kordamp.ikonli.materialdesign2.MaterialDesignA
import ponticello.model.flow.MixerFlow
import ponticello.model.obj.BusObject
import ponticello.model.registry.BusRegistry
import ponticello.sc.NumericalControlSpec
import ponticello.sc.Rate
import ponticello.ui.actions.ServerActions
import ponticello.ui.actions.undoable
import ponticello.ui.controls.Knob
import ponticello.ui.impl.getFrom
import ponticello.ui.registry.ListDisplayConfig
import ponticello.ui.registry.ObjectBox
import ponticello.ui.registry.SearchableBusListView
import reaktive.value.binding.equalTo
import reaktive.value.binding.flatMap
import reaktive.value.binding.not
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import reaktive.value.reactiveValue

class MixerComponentListConfig : ListDisplayConfig<MixerFlow.MixerComponent> {
    private lateinit var mixer: MixerFlow

    private val targetChannels by lazy { mixer.targetBus.flatMap { bus -> bus.get()?.channels ?: reactiveValue(0) } }

    fun setMixer(m: MixerFlow) {
        mixer = m
    }

    fun addSourceBus(ev: MouseEvent) {
        val expectedChannels = mixer.targetBus.now.get()?.channels?.now
        val bus = SearchableBusListView(
            mixer.context[BusRegistry], "Select source bus",
            Rate.Audio, expectedChannels
        ).exclude { mixer.usedBuses() }
            .showPopup(ev) ?: return
        mixer.components.add(MixerFlow.MixerComponent.create(bus))
    }

    override fun acceptedTransferModes(dragboard: Dragboard): Array<TransferMode> {
        val bus = dragboard.getFrom(mixer.context[BusRegistry], BusObject.DATA_FORMAT) ?: return emptyArray()
        val expectedChannels = bus.channels.now == mixer.targetBus.now.get()?.channels?.now
        return if (bus !in mixer.usedBuses() && bus.rate == Rate.Audio && expectedChannels) arrayOf(TransferMode.LINK)
        else emptyArray()
    }

    override fun getDroppedObject(ev: DragEvent): MixerFlow.MixerComponent? {
        val bus = ev.dragboard.getFrom(mixer.context[BusRegistry], BusObject.DATA_FORMAT) ?: return null
        return MixerFlow.MixerComponent.create(bus)
    }

    fun createPanKnob(obj: MixerFlow.MixerComponent, radius: Double): Knob {
        val panKnob = Knob(
            "Balance", obj.pan, NumericalControlSpec.BALANCE, radius,
            color = Color.BLACK, inputMethod = Knob.InputMethod.Horizontal,
            undoManager = mixer.context[UndoManager]
        )
        panKnob.visibleProperty().bind(targetChannels.equalTo(2).asObservableValue())
        return panKnob
    }

    override fun getActions(box: ObjectBox<MixerFlow.MixerComponent>): List<ContextualizedAction> =
        actions.withContext(box.obj)

    companion object {
        val muteAndSolo = collectActions<MixerFlow.MixerComponent> {
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

        private val actions = collectActions<MixerFlow.MixerComponent> {
            add(ServerActions.scopeBus) { f -> f.sourceBus }
            addAll(muteAndSolo)
        }
    }
}