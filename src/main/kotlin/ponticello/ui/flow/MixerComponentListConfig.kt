package ponticello.ui.flow

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.undo.UndoManager
import fxutils.undo.VariableEdit
import javafx.event.Event
import javafx.scene.input.DragEvent
import javafx.scene.input.Dragboard
import javafx.scene.input.MouseEvent
import javafx.scene.input.TransferMode
import javafx.scene.paint.Color
import org.kordamp.ikonli.materialdesign2.MaterialDesignA
import ponticello.impl.Decimal
import ponticello.model.flow.MixerFlow
import ponticello.model.obj.BusObject
import ponticello.model.registry.BusRegistry
import ponticello.model.registry.ObjectList
import ponticello.sc.NumericalControlSpec
import ponticello.sc.Rate
import ponticello.ui.actions.ServerActions
import ponticello.ui.actions.undoable
import ponticello.ui.controls.Knob
import ponticello.ui.impl.getFrom
import ponticello.ui.registry.BusSelectorPrompt
import ponticello.ui.registry.ListDisplayConfig
import ponticello.ui.registry.ObjectBox
import ponticello.ui.registry.ObjectListView
import reaktive.value.ReactiveVariable
import reaktive.value.binding.equalTo
import reaktive.value.fx.asObservableValue
import reaktive.value.now

class MixerComponentListConfig : ListDisplayConfig<MixerFlow.MixerComponent> {
    private var _mixer: MixerFlow? = null

    private val mixer get() = _mixer ?: error("Mixer not set")

    fun setMixer(m: MixerFlow?) {
        _mixer = m
    }

    fun addSourceBus(ev: MouseEvent) {
        val component = createNewObject(ev, mixer.components) ?: return
        mixer.components.add(component)
    }

    override fun createNewObject(ev: Event?, list: ObjectList<MixerFlow.MixerComponent>): MixerFlow.MixerComponent? {
        val expectedChannels = mixer.targetBus.now.get()?.channels?.now
        val bus = BusSelectorPrompt(
            mixer.context[BusRegistry], "Select source bus",
            Rate.Audio, expectedChannels
        ).exclude { mixer.usedBuses() }
            .showPopup(ev) ?: return null
        return MixerFlow.MixerComponent.create(bus)
    }

    override fun acceptedTransferModes(dragboard: Dragboard): Array<TransferMode> {
        val bus = dragboard.getFrom(mixer.context[BusRegistry], BusObject.DATA_FORMAT) ?: return emptyArray()
        val expectedChannels = bus.channels.now == mixer.targetBus.now.get()?.channels?.now
        return if (bus !in mixer.usedBuses() && bus.rate == Rate.Audio && expectedChannels) arrayOf(TransferMode.LINK)
        else emptyArray()
    }

    override fun getDroppedObjects(
        ev: DragEvent,
        targetView: ObjectListView<MixerFlow.MixerComponent>
    ): List<MixerFlow.MixerComponent> {
        val bus = ev.dragboard.getFrom(mixer.context[BusRegistry], BusObject.DATA_FORMAT) ?: return emptyList()
        return listOf(MixerFlow.MixerComponent.create(bus))
    }

    fun createPanKnob(variable: ReactiveVariable<Decimal>, radius: Double): Knob {
        val panKnob = Knob(
            "Balance", variable, NumericalControlSpec.BALANCE, radius,
            color = Color.BLACK, inputMethod = Knob.InputMethod.Horizontal,
            undoManager = mixer.context[UndoManager]
        )
        panKnob.visibleProperty().bind(mixer.targetChannels.equalTo(2).asObservableValue())
        return panKnob
    }

    override fun getActions(box: ObjectBox<MixerFlow.MixerComponent>): List<ContextualizedAction> =
        actions.withContext(box.obj)

    companion object {
        val muteAndSolo = collectActions<MixerFlow.MixerComponent> {
            addAction("Toggle mute") {
                icon(MaterialDesignA.ALPHA_M_BOX)
                toggleState { comp -> comp.state.equalTo(MixerFlow.MixerComponentMode.Mute) }
                executes { comp ->
                    val state = when (comp.state.now) {
                        MixerFlow.MixerComponentMode.Mute -> MixerFlow.MixerComponentMode.Regular
                        else -> MixerFlow.MixerComponentMode.Mute
                    }
                    VariableEdit.updateVariable(comp.state, state, comp.context[UndoManager], "Toggle mute")
                }
                undoable()
            }
            addAction("Toggle solo") {
                icon(MaterialDesignA.ALPHA_S_BOX)
                toggleState { comp -> comp.state.equalTo(MixerFlow.MixerComponentMode.Solo) }
                executes { comp ->
                    val state = when (comp.state.now) {
                        MixerFlow.MixerComponentMode.Solo -> MixerFlow.MixerComponentMode.Regular
                        else -> MixerFlow.MixerComponentMode.Solo
                    }
                    VariableEdit.updateVariable(comp.state, state, comp.context[UndoManager], "Toggle solo")
                }
                undoable()
            }
        }

        private val actions = collectActions<MixerFlow.MixerComponent> {
            add(ServerActions.scopeBus) { f -> f.sourceBus }
            addAll(muteAndSolo)
        }
    }
}