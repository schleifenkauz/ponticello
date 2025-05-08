package xenakis.ui.registry

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.controls.SliderBar
import fxutils.undo.UndoManager
import javafx.event.Event
import javafx.geometry.Point2D
import javafx.scene.Node
import javafx.scene.layout.HBox
import org.kordamp.ikonli.codicons.Codicons
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import reaktive.ObserverMap
import reaktive.value.binding.impl.notNull
import reaktive.value.binding.map
import reaktive.value.forEach
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.toDecimal
import xenakis.model.obj.BusObject
import xenakis.model.registry.BusRegistry
import xenakis.sc.NumericalControlSpec
import xenakis.ui.controls.ControlSpecPrompt

class ControlBusRegistryPane(busses: BusRegistry) : AbstractBusRegistryPane(busses) {
    private val specObservers = ObserverMap<BusObject.ControlBus>()

    init {
        setup()
    }

    override fun createNewObject(name: String, ev: Event?): BusObject = BusObject.control(name, 1)

    override fun filter(obj: BusObject): Boolean = obj is BusObject.ControlBus

    override fun onRemoved(obj: BusObject) {
        if (obj is BusObject.ControlBus) specObservers.remove(obj)
    }

    override fun getItemContent(obj: BusObject): List<Node> {
        if (obj !is BusObject.ControlBus) return emptyList()
        val defaultValue = reactiveVariable(obj.spec.now?.defaultValue?.get() ?: 0.0.toDecimal())
        val name = obj.name.map { n -> "Default value for $n" }
        val sliderBox = HBox()
        sliderBox.prefWidth = 150.0
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
        return super.getItemContent(obj) + sliderBox
    }

    override fun getActions(box: ObjectBox<BusObject>): List<ContextualizedAction> {
        return if (box.obj is BusObject.ControlBus) {
            @Suppress("UNCHECKED_CAST")
            val cast = box as ObjectBox<BusObject.ControlBus>
            super.getActions(box) + actions.withContext(cast)
        } else super.getActions(box)
    }

    companion object {
        private val actions = collectActions<ObjectBox<BusObject.ControlBus>> {
            addAction("Remove default value") {
                icon(Material2AL.CLOSE)
                enableWhen { box -> box.obj.spec.notNull() }
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