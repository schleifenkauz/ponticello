package ponticello.ui.registry

import fxutils.actions.Action
import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.addAfter
import fxutils.controls.IntSpinner
import fxutils.controls.SliderBar
import fxutils.label
import fxutils.prompt.IntegerPrompt
import fxutils.prompt.SimpleSelectorPrompt
import fxutils.styleClass
import fxutils.undo.UndoManager
import javafx.event.Event
import javafx.geometry.Point2D
import javafx.scene.Node
import javafx.scene.input.DataFormat
import javafx.scene.input.Dragboard
import javafx.scene.input.TransferMode
import javafx.scene.layout.HBox
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.codicons.Codicons
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import ponticello.impl.toDecimal
import ponticello.model.instr.BusObject
import ponticello.model.obj.superColliderName
import ponticello.model.project.BUSSES
import ponticello.model.project.PonticelloProject
import ponticello.model.project.busses
import ponticello.model.server.BusRegistry
import ponticello.sc.NumericalControlSpec
import ponticello.sc.Rate
import ponticello.sc.client.SuperColliderClient
import ponticello.ui.controls.ControlSpecPrompt
import ponticello.ui.dock.BusRegistryPaneState
import ponticello.ui.dock.Side
import ponticello.ui.dock.ToolPane
import ponticello.ui.dock.ToolPaneState
import reaktive.ObserverMap
import reaktive.and
import reaktive.value.binding.impl.notNull
import reaktive.value.binding.map
import reaktive.value.forEach
import reaktive.value.now
import reaktive.value.reactiveVariable

class BusRegistryPane(busses: BusRegistry) : ObjectRegistryPane<BusObject>(busses, BUSSES.serializer) {
    override val type: Type
        get() = BusRegistryPane

    private val specObservers = ObserverMap<BusObject.ControlBus>()

    private var filter = BusTypeFilter.All
        set(value) {
            field = value
            listView.refilter()
        }

    private val filterSelector = SimpleSelectorPrompt(BusTypeFilter.entries, "Select filter")
        .selectorButton(this::filter)

    override fun defaultState(): ToolPaneState = BusRegistryPaneState.default()

    override fun doSetup() {
        super.doSetup()
        val state = initialState
        if (state is BusRegistryPaneState) {
            filter = state.filter
        }
    }

    override fun afterSetup() {
        super.afterSetup()
        header.children.addAfter(searchText, filterSelector)
    }

    override fun saveState(dest: ToolPaneState) {
        super.saveState(dest)
        if (dest is BusRegistryPaneState) {
            dest.filter = filter
        }
    }

    override fun onRemoved(obj: BusObject) {
        if (obj is BusObject.ControlBus) specObservers.remove(obj)
    }

    override fun filter(obj: BusObject): Boolean = super.filter(obj) && filter.filter(obj)

    override fun createNewObject(name: String, ev: Event?): BusObject? {
        val type = SimpleSelectorPrompt(Rate.entries, "Bus type")
            .showPopup(ev) ?: return null

        val channels = IntegerPrompt("Channels", initialValue = 2, range = 1..16)
            .showDialog(ev) ?: return null
        return BusObject.create(type, name, channels)
    }

    override fun getHeaderContent(obj: BusObject): List<Node> = buildList {
        val channelsSpinner = IntSpinner(obj.channels, 1, 12).minColumns(2)
            .setupUndo("Bus Channels", obj.context[UndoManager])
        channelsSpinner.isDisable = obj.busType != BusObject.Type.Regular
        add(channelsSpinner)
        val rateString = when (obj.rate) {
            Rate.Audio -> "ar"
            Rate.Control -> "kr"
        }
        add(label(rateString) styleClass "rate-label")
        if (obj is BusObject.ControlBus) {
            val defaultValue = reactiveVariable(obj.spec.now?.defaultValue?.get() ?: 0.0.toDecimal())
            val name = obj.name.map { n -> "Default value for $n" }
            val sliderBox = HBox()
            var previousSpec: NumericalControlSpec? = null
            specObservers[obj] = obj.spec.forEach { spec ->
                if (spec != null) {
                    defaultValue.now = spec.defaultValue.get()
                    if (previousSpec?.copy(defaultValue = spec.defaultValue) != spec) {
                        val slider = SliderBar(
                            defaultValue, name, spec.converter(), SliderBar.Style.AlwaysValue,
                            undoManager = registry.context[UndoManager]
                        )
                        slider.prefWidth = 150.0
                        sliderBox.children.add(slider)
                    }
                } else {
                    sliderBox.children.clear()
                }
                previousSpec = spec
            } and defaultValue.observe { _, _, newValue ->
                obj.setDefaultValue(newValue)
            }
            add(sliderBox)
        }
    }

    override val dataFormat: DataFormat
        get() = BusObject.DATA_FORMAT

    override fun acceptedTransferModes(dragboard: Dragboard): Array<TransferMode> = emptyArray()

    override fun getActions(box: ObjectBox<BusObject>): List<ContextualizedAction> {
        return if (box.obj is BusObject.ControlBus) {
            @Suppress("UNCHECKED_CAST")
            val cast = box as ObjectBox<BusObject.ControlBus>
            controlBusActions.withContext(cast) + actions.withContext(box)
        } else actions.withContext(box)
    }

    enum class BusTypeFilter {
        All, Audio, Control;

        fun filter(bus: BusObject) = when (this) {
            All -> true
            Audio -> bus is BusObject.AudioBus
            Control -> bus is BusObject.ControlBus
        }
    }

    companion object : Type(uid = 3, "Busses") {
        override val defaultSide: Side
            get() = Side.LEFT

        override val icon: Ikon
            get() = Material2AL.GRAPHIC_EQ //MaterialDesignT.TUNE_VARIANT

        override val shortcut: String
            get() = "F4"

        override fun createToolPane(project: PonticelloProject): ToolPane = BusRegistryPane(project.busses)

        private val actions = collectActions<ObjectBox<BusObject>> {
            addAction("Monitor bus") {
                icon(MaterialDesignP.PULSE)
                shortcut("Ctrl+M")
                executes { box ->
                    val bus = box.obj
                    bus.context[SuperColliderClient].run("{ ${bus.superColliderName}.scope }.defer;")
                }
            }
        }

        private val controlBusActions = collectActions<ObjectBox<BusObject.ControlBus>> {
            addAction("Remove default value") {
                icon(Material2AL.CLOSE)
                enableWhen { box -> box.obj.spec.notNull() }
                ifNotApplicable(Action.IfNotApplicable.Hide)
                executes { box -> box.obj.updateSpec(null) }
            }
            addAction("Configure default value") {
                icon { box ->
                    box.obj.spec.map { spec -> if (spec == null) Material2MZ.PLUS else Codicons.SYMBOL_PROPERTY }
                }
                executes { box ->
                    val bus = box.obj
                    val name = bus.name.now
                    val initialSpec = bus.spec.now ?: NumericalControlSpec.DEFAULT
                    val spec = ControlSpecPrompt.create(name, null, initialSpec)!!
                        .showDialog(box, offset = Point2D(box.width, 0.0)) ?: return@executes
                    bus.updateSpec(spec as NumericalControlSpec)
                }
            }
        }
    }
}