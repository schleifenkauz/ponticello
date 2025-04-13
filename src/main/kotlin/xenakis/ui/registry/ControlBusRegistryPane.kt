package xenakis.ui.registry

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.centerChildren
import fxutils.controls.SliderBar
import fxutils.hspace
import fxutils.setFixedWidth
import javafx.geometry.Point2D
import javafx.scene.Node
import javafx.scene.control.Spinner
import javafx.scene.layout.HBox
import org.kordamp.ikonli.codicons.Codicons
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import reaktive.ObserverMap
import reaktive.value.binding.impl.notNull
import reaktive.value.binding.map
import reaktive.value.forEach
import reaktive.value.fx.asProperty
import reaktive.value.now
import reaktive.value.reactiveValue
import reaktive.value.reactiveVariable
import xenakis.impl.toDecimal
import xenakis.model.obj.BusObject
import xenakis.model.registry.BusRegistry
import xenakis.sc.NumericalControlSpec
import xenakis.sc.client.SuperColliderClient
import xenakis.ui.controls.ControlSpecPrompt

class ControlBusRegistryPane(busses: BusRegistry) : ObjectRegistryPane<BusObject>(busses) {
    private val specObservers = ObserverMap<BusObject.ControlBus>()

    init {
     setup()
    }

    override fun createNewObject(name: String): BusObject = BusObject.control(name, 1)

    override val enableReordering: Boolean
        get() = true

    override fun filter(obj: BusObject): Boolean = obj is BusObject.ControlBus

    override fun getItemContent(obj: BusObject): List<Node> {
        if (obj !is BusObject.ControlBus) return emptyList()
        val channelsSpinner = Spinner<Int>(1, 12, 2).setFixedWidth(60.0)
        channelsSpinner.valueFactory.valueProperty().bindBidirectional(obj.channels.asProperty())
        val space = hspace(SLIDER_WIDTH)
        val box = HBox(5.0, channelsSpinner, space).centerChildren()
        val defaultValue = reactiveVariable(obj.spec.now?.defaultValue?.get() ?: 0.0.toDecimal())
        val name = obj.name.map { n -> "Default value for $n" }
        var previousSpec: NumericalControlSpec? = null
        specObservers[obj] = obj.spec.forEach { spec ->
            if (spec != null) {
                defaultValue.now = spec.defaultValue.get()
                if (previousSpec?.copy(defaultValue = spec.defaultValue) != spec) {
                    val slider = SliderBar(defaultValue, name, spec.converter(), SliderBar.Style.AlwaysValue)
                    slider.prefWidth = 150.0
                    box.children[1] = slider
                }
            } else {
                box.children[1] = space
            }
            previousSpec = spec
        } and defaultValue.observe { _, _, newValue ->
            obj.setDefaultValue(newValue)
        }
        return listOf(box)
    }

    override fun onRemoved(obj: BusObject) {
        if (obj is BusObject.ControlBus) specObservers.remove(obj)
    }

    override fun getActions(box: ObjectBox<BusObject>): List<ContextualizedAction> = actions.withContext(box)

    companion object {
        private const val SLIDER_WIDTH = 150.0

        private val actions = collectActions<ObjectBox<BusObject>> {
            addAction("Remove default value") {
                icon(Material2AL.CLOSE)
                applicableIf { box ->
                    if (box.obj !is BusObject.ControlBus) reactiveValue(false)
                    else box.obj.spec.notNull()
                }
                executes { box ->
                    val bus = box.obj as BusObject.ControlBus
                    bus.updateSpec(null)
                }
            }
            addAction("Configure default value") {
                icon { box ->
                    if (box.obj !is BusObject.ControlBus) reactiveValue(null)
                    else box.obj.spec.map { spec -> if (spec == null) Material2MZ.PLUS else Codicons.SYMBOL_PROPERTY }
                }
                executes { box ->
                    val bus = box.obj as BusObject.ControlBus
                    val name = bus.name.now
                    val initialSpec = bus.spec.now ?: NumericalControlSpec.DEFAULT
                    val spec = ControlSpecPrompt.create(name, null, initialSpec)!!
                        .showDialog(box, offset = Point2D(box.width, 0.0)) ?: return@executes
                    bus.updateSpec(spec as NumericalControlSpec)
                }
            }
            addAction("Monitor bus") {
                icon(MaterialDesignP.PULSE)
                shortcut("Ctrl+M")
                executes { box ->
                    val bus = box.obj
                    bus.context[SuperColliderClient].run("${bus.superColliderName}.scope;")
                }
            }
        }
    }
}