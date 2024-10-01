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
class GlobalControls(private val controls: MutableList<GlobalControl>) : XenakisProject.ProjectComponent {
    @Transient
    val views = ListenerManager.createWeakListenerManager<GlobalControlsView>()

    @Transient
    private val valueObservers = mutableMapOf<GlobalControl, Observer>()

    @Transient
    private lateinit var context: Context

    override val componentName: String
        get() = "global_controls"

    fun initialize(context: Context) {
        this.context = context
        for (ctrl in controls) {
            valueObservers[ctrl] = ctrl.knobControl.value.forEach { value -> updatedValue(ctrl, value) }
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
        context[SuperColliderClient].run("if (${bus.superColliderName} != nil) { ${bus.superColliderName}.set($formatted) };")
    }

    fun updateControlFromServer(control: GlobalControl) {
        val code = "${control.bus.superColliderName}.getSynchronous"
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