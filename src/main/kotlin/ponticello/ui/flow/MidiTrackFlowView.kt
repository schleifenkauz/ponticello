package ponticello.ui.flow

import fxutils.*
import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.actions.makeButton
import fxutils.prompt.SelectorPrompt
import hextant.context.Context
import javafx.event.Event
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
import ponticello.model.registry.ObjectList
import ponticello.ui.dock.AppLayout
import ponticello.ui.midi.*
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
import reaktive.value.reactiveValue

class MidiTrackFlowView(private val flow: MidiTrackFlow) : VBox(), ListDisplayConfig<MidiInstrument> {
    private val instrumentsView = ObjectListView(flow.instruments, this)

    init {
        val addInstrButton = addObjectAction.withContext(instrumentsView).makeButton("small-icon-button")
        val layout = VBox(addInstrButton.centered(), instrumentsView).alwaysVGrow()
        children.addAll(layout)
    }

    override val inlineOrientation: Orientation
        get() = Orientation.VERTICAL

    override val supportedModes: Collection<ObjectListView.DisplayMode>
        get() = listOf(ObjectListView.DisplayMode.Collapsable)

    override val displayName: Boolean get() = false

    override fun emptyDisplay(view: ObjectListView<MidiInstrument>): Node = Region()

    override fun getActions(box: ObjectBox<MidiInstrument>): List<ContextualizedAction> =
        instrumentActions.withContext(box.obj) + listOf(removeObjectAction.withContext(box))

    override val buttonStyle: String get() = "small-icon-button"

    override fun getContent(obj: MidiInstrument, box: ObjectBox<MidiInstrument>): Parent? = when (val obj = box.obj) {
        is SoundProcessMidiInstrument -> ParameterControlsPane(obj).pad(3.0) //TODO midi context
        is VSTMidiInstrument -> null //TODO something?
    }

    override fun getHeaderContent(obj: MidiInstrument): List<Node> = when (obj) {
        is SoundProcessMidiInstrument -> {
            val selectorButton = InstrumentSelectorPopup(obj.context).selectorButton(obj.reference)
            listOf(
                label(obj.reference.map { it.get()?.instrumentType ?: "<unresolved>" }),
                selectorButton
            )
        }

        is VSTMidiInstrument -> {
            val selectorButton = VSTInstrumentSelectorPrompt(obj.context).selectorButton(obj::flow)
            listOf(
                Label("VST: "),
                selectorButton
            )
        }
    }

    /* TODO remove
        override fun expandedLayout(box: ObjectBox<MidiInstrument>): Node {
            val header = HBox(
                createInstrumentSelectorButton(box.obj),
                infiniteSpace(),
                removeObjectAction.withContext(box).makeButton("small-icon-button")
            )
            return VBox(3.0, header, box.content).pad(3.0)
        }
    */

    override fun createSeparatorNode(box: ObjectBox<MidiInstrument>): Node {
        val button = insertObjectAction.withContext(box).makeButton("small-icon-button")
        return HBox(infiniteSpace(), button, infiniteSpace())
    }

    override fun createNewObject(ev: Event?, list: ObjectList<MidiInstrument>): MidiInstrument? =
        MidiInstrumentSelectorPrompt(flow.context, "Insert instrument")
            .showPopup(ev)
            ?.createInstrument()

    class MidiDeviceSelectorPrompt(
        private val type: MidiDeviceSpec.Type,
        private val context: Context
    ) : SelectorPrompt<MidiDeviceSpec>("Select ${type.name.lowercase()} device") {
        override fun options(): List<MidiDeviceSpec> = MidiDeviceSpec.getOptions(type, context)

        override fun displayText(option: MidiDeviceSpec): String = when (option) {
            is MidiDeviceSpec.ByName -> option.name
            MidiDeviceSpec.None -> "<none>"
        }

        override fun extractText(option: MidiDeviceSpec): String = when (option) {
            is MidiDeviceSpec.ByName -> option.name
            MidiDeviceSpec.None -> ""
        }
    }

    companion object {
        private val instrumentActions = collectActions<MidiInstrument> {
            addAction("View instrument") {
                icon { obj ->
                    when (obj) {
                        is SoundProcessMidiInstrument -> reactiveValue(Codicons.CODE)
                        is VSTMidiInstrument -> reactiveValue(MaterialDesignE.EYE)
                    }
                }
                executes { obj ->
                    when (obj) {
                        is SoundProcessMidiInstrument -> {
                            val pane = obj.context[AppLayout].get<InstrumentRegistryPane>()
                            pane.showContent(obj.getInstrument())
                        }

                        is VSTMidiInstrument -> {
                            val flow = obj.vst
                            flow?.showEditor()
                        }
                    }
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