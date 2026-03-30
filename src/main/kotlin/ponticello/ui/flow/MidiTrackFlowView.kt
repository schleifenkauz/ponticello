package ponticello.ui.flow

import fxutils.*
import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.actions.makeButton
import fxutils.prompt.PromptPlacement
import fxutils.prompt.SimpleSelectorPrompt
import fxutils.undo.UndoManager
import javafx.geometry.Orientation
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import org.kordamp.ikonli.codicons.Codicons
import org.kordamp.ikonli.materialdesign2.MaterialDesignE
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import ponticello.model.flow.MidiTrackFlow
import ponticello.model.midi.*
import ponticello.model.registry.ObjectList
import ponticello.ui.dock.AppLayout
import ponticello.ui.live.GridBanksBar
import ponticello.ui.live.MidiGridPane
import ponticello.ui.midi.MidiContext
import ponticello.ui.midi.MidiEffectSelectorPrompt
import ponticello.ui.midi.NewMidiInstrumentPrompt
import ponticello.ui.registry.InstrumentRegistryPane
import ponticello.ui.registry.ListDisplayConfig
import ponticello.ui.registry.ListDisplayConfig.Companion.addObjectAction
import ponticello.ui.registry.ListDisplayConfig.Companion.insertObjectAction
import ponticello.ui.registry.ObjectBox
import ponticello.ui.registry.ObjectBox.Companion.removeObjectAction
import ponticello.ui.registry.ObjectListView
import ponticello.ui.score.ParameterControlsPane
import reaktive.value.binding.`if`
import reaktive.value.binding.map
import java.util.*

class MidiTrackFlowView(private val flow: MidiTrackFlow) : VBox(), ListDisplayConfig<MidiInstrument> {
    private val instrumentsView = ObjectListView(flow.instruments, this)
    private var midiContexts: MutableMap<MidiInstrument, MidiContext?>? = null

    init {
        val addInstrButton = addObjectAction.withContext(instrumentsView).makeButton("small-icon-button")
        val layout = VBox(addInstrButton.centered(), instrumentsView).alwaysVGrow()
        children.addAll(layout)
    }

    override val inlineOrientation: Orientation
        get() = Orientation.VERTICAL

    override val supportedModes: Collection<ObjectListView.DisplayMode>
        get() = listOf(ObjectListView.DisplayMode.Collapsable)

    override fun displayName(obj: MidiInstrument): Boolean = false

    override fun emptyDisplay(view: ObjectListView<MidiInstrument>): Node = Region()

    override fun getActions(box: ObjectBox<MidiInstrument>): List<ContextualizedAction> =
        instrumentActions.withContext(box.obj) + listOf(removeObjectAction.withContext(box))

    override val buttonStyle: String get() = "small-icon-button"

    private fun midiContext(obj: MidiInstrument): MidiContext? {
        if (midiContexts == null) midiContexts = WeakHashMap()
        return midiContexts!!.getOrPut(obj) { obj.midiContext() }
    }

    override fun getContent(obj: MidiInstrument, box: ObjectBox<MidiInstrument>): Parent? = when (val obj = box.obj) {
        is VSTMidiInstrument -> VSTPluginFlowView(obj.vst, showPluginSelector = false)

        is MidiGridInstrument -> MidiGridPane(obj)

        is ParameterizedMidiInstrument -> ParameterControlsPane(obj, midiContext = midiContext(obj)).pad(3.0)
    }

    override fun getHeaderContent(obj: MidiInstrument): List<Node> = when (obj) {
        is SoundProcessMidiInstrument -> {
            val selectorButton = InstrumentSelectorPopup(obj.context).selectorButton(obj.reference)
            listOf(
                label(obj.reference.map { it.get()?.instrumentType ?: "<unresolved>" }),
                hspace(1.0),
                selectorButton
            )
        }

        is VSTMidiInstrument -> listOf(
            Label("VST"),
            hspace(1.0),
            VSTPluginFlowView.createPluginSelectorBar(obj.vst)
        )

        is MidiEffectObject -> listOf(
            Label("MIDI Effect"),
            hspace(1.0),
            MidiEffectSelectorPrompt(obj.context).selectorButton(obj.reference)
        )

        is MidiGridInstrument -> {
            val availableTargets = MidiGridInstrument.Target.options(obj.context)
            val targetSelector = SimpleSelectorPrompt(availableTargets, "Choose target")
                .selectorButton(obj.target, undoManager = obj.context[UndoManager])
            listOf(Label("Grid"), GridBanksBar(obj), targetSelector)
        }
    }

    override fun createSeparatorNode(box: ObjectBox<MidiInstrument>): Node {
        val button = insertObjectAction.withContext(box).makeButton("small-icon-button")
        return HBox(infiniteSpace(), button, infiniteSpace())
    }

    override fun createNewObject(promptPlacement: PromptPlacement, list: ObjectList<MidiInstrument>): MidiInstrument? =
        NewMidiInstrumentPrompt(flow.context, "Insert instrument")
            .showPopup(promptPlacement)?.createInstrument(flow.context, promptPlacement)

    companion object {
        private val instrumentActions = collectActions<MidiInstrument> {
            addAction("Show VST editor") {
                icon(MaterialDesignE.EYE)
                shortcut("I")
                executesOn { instr: VSTMidiInstrument ->
                    instr.vst.showEditor()
                }
            }
            addAction("View instrument") {
                icon(Codicons.CODE)
                shortcut("I")
                executesOn { instr: ParameterizedMidiInstrument ->
                    val pane = instr.context[AppLayout].get<InstrumentRegistryPane>()
                    pane.showContent(instr.getInstrument())
                }
            }
            addAction("Toggle active") {
                icon { instr ->
                    `if`(
                        instr.isEnabled,
                        then = { MaterialDesignR.RADIOBOX_MARKED },
                        otherwise = { MaterialDesignR.RADIOBOX_BLANK }
                    )
                }
                description { instr ->
                    `if`(
                        instr.isEnabled,
                        then = { "Deactivate" },
                        otherwise = { "Activate" }
                    )
                }
                executes { instr -> instr.toggleEnabled() }
            }
        }
    }
}