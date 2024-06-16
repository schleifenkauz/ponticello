package xenakis.model

import hextant.context.Context
import hextant.core.editor.ListenerManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.Observer
import reaktive.value.forEach
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.ScWriter
import xenakis.impl.SuperColliderClient
import xenakis.impl.SuperColliderContext
import xenakis.sc.NumericalControlSpec
import xenakis.sc.Rate
import xenakis.ui.format

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
            valueObservers[ctrl] = ctrl.knobControl.value.forEach { value -> updatedValue(ctrl, value) }
        }
    }

    fun ScWriter.setBusValues() {
        for (control in controls) {
            setupBus(control)
        }
    }

    private fun SuperColliderContext.setupBus(control: GlobalControl) {
        val bus = control.bus
        val varName = bus.variableName
        val value = control.knobControl.get()
        run("$varName.set($value);")
    }

    fun addControl(parameter: String, spec: NumericalControlSpec) {
        val knobControl = KnobControl(reactiveVariable(spec.defaultValue.get()))
        val control = GlobalControl(parameter, knobControl, spec)
        context[BusRegistry].add(control.bus)
        controls.add(control)
        context[SuperColliderClient].setupBus(control)
        views.notifyListeners { addedControl(control) }
        valueObservers[control] = control.knobControl.value.forEach { value -> updatedValue(control, value) }
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

    private fun updatedValue(control: GlobalControl, value: Double) {
        val bus = control.bus
        val formatted = value.format(control.spec.accuracy)
        context[SuperColliderClient].run("if (${bus.variableName} != nil) { ${bus.variableName}.set($formatted) };")
    }

    fun updateControlFromServer(control: GlobalControl) {
        val code = "${control.bus.variableName}.getSynchronous"
        context[SuperColliderClient].eval(code).thenAccept { answer ->
            val value = answer.toDoubleOrNull() ?: return@thenAccept
            control.knobControl.value.now = value
        }
    }

    fun hasControl(name: String): Boolean = controls.any { ctrl -> ctrl.parameter == name }

    @Serializable
    class GlobalControl(val parameter: String, val knobControl: KnobControl, val spec: NumericalControlSpec) {
        val bus: BusObject = BusObject.create("global_$parameter", Rate.Control, 1)
    }
}