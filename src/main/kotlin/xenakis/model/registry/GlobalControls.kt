package xenakis.model.registry

import hextant.context.Context
import hextant.core.editor.ListenerManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.Observer
import reaktive.value.ReactiveVariable
import reaktive.value.forEach
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.Decimal
import xenakis.impl.parseDecimal
import xenakis.impl.withPrecision
import xenakis.model.obj.BusObject
import xenakis.sc.NumericalControlSpec
import xenakis.sc.client.ScWriter
import xenakis.sc.client.SuperColliderClient
import xenakis.sc.client.SuperColliderContext
import xenakis.ui.registry.GlobalControlsView

@Serializable
class GlobalControls(private val controls: MutableList<GlobalControl>) {
    @Transient
    val views = ListenerManager.createWeakListenerManager<GlobalControlsView>()

    @Transient
    private val valueObservers = mutableMapOf<GlobalControl, Observer>()

    @Transient
    private lateinit var context: Context

    fun initialize(context: Context) {
        this.context = context
        for (ctrl in controls) {
            valueObservers[ctrl] = ctrl.value.forEach { value -> updatedValue(ctrl, value) }
        }
        context[SuperColliderClient].run { setBusValues(writer) }
    }

    fun setBusValues(writer: ScWriter) = writer.run {
        +"if (~init_global_controls != nil) { ServerTree.remove(~init_global_controls) }"
        appendBlock("~init_global_controls = ") {
            for (control in controls) {
                setupBus(control)
            }
        }
        +"ServerTree.add(~init_global_controls)"
        +"if (s.serverRunning) { ~init_global_controls.value }"
    }

    private fun SuperColliderContext.setupBus(control: GlobalControl) {
        val bus = control.bus
        val varName = bus.superColliderName
        val value = control.value.get()
        run("$varName.set($value);")
    }

    fun addControl(parameter: String, spec: NumericalControlSpec) {
        val value = (reactiveVariable(spec.defaultValue.get()))
        val control = GlobalControl(parameter, value, spec)
        context[BusRegistry].add(control.bus)
        controls.add(control)
        context[SuperColliderClient].setupBus(control)
        views.notifyListeners { addedControl(control) }
        valueObservers[control] = control.value.forEach { value -> updatedValue(control, value) }
    }

    fun removeControl(control: GlobalControl) {
        controls.remove(control)
        views.notifyListeners { removedControl(control) }
        val bus = control.bus
        context[BusRegistry].remove(bus)
        valueObservers.remove(control)?.kill()
    }

    fun addView(view: GlobalControlsView) {
        views.addListener(view)
        for (control in controls) {
            view.addedControl(control)
        }
    }

    private fun updatedValue(control: GlobalControl, value: Decimal) {
        val bus = control.bus
        context[SuperColliderClient].run("if (${bus.superColliderName} != nil) { ${bus.superColliderName}.set($value) };")
    }

    fun updateControlFromServer(control: GlobalControl) {
        val code = "${control.bus.superColliderName}.getSynchronous"
        val value = context[SuperColliderClient].eval(code).get().parseDecimal() ?: return
        control.value.now = value.withPrecision(control.spec.precision)
    }

    fun hasControl(name: String): Boolean = controls.any { ctrl -> ctrl.parameter == name }

    @Serializable
    class GlobalControl(val parameter: String, val value: ReactiveVariable<Decimal>, val spec: NumericalControlSpec) {
        val bus: BusObject = BusObject.audio("global_$parameter", 1)
    }
}