package xenakis.ui.flow

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.prompt.IntegerPrompt
import fxutils.setFixedWidth
import javafx.geometry.Orientation
import javafx.geometry.Point2D
import javafx.scene.Node
import javafx.scene.control.Spinner
import org.kordamp.ikonli.evaicons.Evaicons
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import reaktive.value.fx.asObservableValue
import reaktive.value.fx.asProperty
import reaktive.value.now
import xenakis.model.flow.AudioFlows
import xenakis.model.obj.BusObject
import xenakis.model.registry.BusRegistry
import xenakis.sc.client.SuperColliderClient
import xenakis.ui.actions.RegistryObjectActions
import xenakis.ui.controls.NamePrompt
import xenakis.ui.registry.NamedObjectListView.DisplayMode
import xenakis.ui.registry.ObjectBox
import xenakis.ui.registry.SearchableToolPane

class AudioFlowPane(
    private val flows: AudioFlows,
) : SearchableToolPane<BusObject>() {
    private val buses = flows.context[BusRegistry]

    override val inlineOrientation: Orientation
        get() = Orientation.HORIZONTAL

    override val supportedModes: Set<DisplayMode>
        get() = setOf(DisplayMode.Inline, DisplayMode.SubWindow, DisplayMode.DetailsPane)

    init {
        styleClass.add("flow-pane")
        setup(title = null, buses) { headerActions.withContext(this) }
        listView.itemsScrollPane.isFitToHeight = true
        listView.autoResizeScene = true
    }

    override fun filter(obj: BusObject): Boolean = obj is BusObject.AudioBus

    override fun getContent(obj: BusObject, mode: DisplayMode) = FlowChainView(flows, obj)

    override fun getItemContent(obj: BusObject): List<Node> {
        val channelsSpinner = Spinner<Int>(0, 64, obj.channels.now).setFixedWidth(65.0)
        if (obj.busType != BusObject.Type.Regular) {
            channelsSpinner.valueFactory.valueProperty().bind(obj.channels.asObservableValue())
            channelsSpinner.isDisable = true
        } else {
            channelsSpinner.valueFactory.valueProperty().bindBidirectional(obj.channels.asProperty())
        }
        return listOf(channelsSpinner)
    }

    override fun getActions(box: ObjectBox<BusObject>): List<ContextualizedAction> = actions.withContext(box.obj)

    override fun createNewObject(): BusObject? {
        val busName = NamePrompt(buses, "Bus name", "")
            .showDialog(header!!, offset = Point2D(200.0, 0.0)) ?: return null
        val channels = IntegerPrompt("Channels", initialValue = 2, range = 1..64)
            .showDialog(actionBar) ?: return null
        return BusObject.audio(busName, channels)
    }

    companion object {
        private val actions = collectActions<BusObject> {
            addAction("Monitor bus") {
                icon(Evaicons.ACTIVITY)
                shortcut("Ctrl+M")
                executes { bus ->
                    bus.context[SuperColliderClient].run("${bus.superColliderName}.scope;")
                }
            }
            add(RegistryObjectActions.deleteAction(BusRegistry))
        }

        private val headerActions = collectActions<AudioFlowPane> {
            addAction("Create new audio bus") {
                shortcut("Ctrl+PLUS")
                icon(MaterialDesignP.PLUS)
                executes { p ->
                    val bus = p.createNewObject() ?: return@executes
                    p.buses.add(bus)
                }
            }
        }
    }
}