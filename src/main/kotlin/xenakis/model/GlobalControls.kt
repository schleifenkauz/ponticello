package xenakis.model

import hextant.context.Context
import hextant.core.editor.ListenerManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import xenakis.impl.ScWriter
import xenakis.impl.SuperColliderClient
import xenakis.impl.SuperColliderContext
import xenakis.sc.NumericalControlSpec
import xenakis.sc.Rate
import xenakis.ui.KnobControlView
import xenakis.ui.format

@Serializable
class GlobalControls(private val controls: MutableList<GlobalControl>) : KnobControlView {
    @Transient
    val views = ListenerManager.createWeakListenerManager<GlobalControlsView>()

    @Transient
    private lateinit var context: Context

    fun initialize(context: Context) {
        this.context = context
        for (ctrl in controls) {
            ctrl.knobControl.addView(this)
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
        val knobControl = KnobControl(spec.defaultValue.get())
        val control = GlobalControl(parameter, knobControl, spec)
        context[BusRegistry].add(control.bus)
        controls.add(control)
        context[SuperColliderClient].setupBus(control)
        views.notifyListeners { addedControl(control) }
        knobControl.addView(this)
    }

    fun removeControl(control: GlobalControl) {
        controls.remove(control)
        views.notifyListeners { removedControl(control) }
        val bus = control.bus
        context[BusRegistry].remove(bus)
        control.knobControl.views.removeListener(this)
    }

    fun addView(view: GlobalControlsView) {
        views.addListener(view)
        for (control in controls) {
            view.addedControl(control)
        }
    }

    override fun updatedValue(control: KnobControl, value: Double) {
        val ctrl = controls.find { it.knobControl == control } ?: error("$control not found in global controls")
        val bus = ctrl.bus
        val formatted = value.format(ctrl.spec.accuracy)
        context[SuperColliderClient].run("if (${bus.variableName} != nil) { ${bus.variableName}.set($formatted) };")
    }

    fun updateControlFromServer(control: GlobalControl) {
        val code = "${control.bus.variableName}.getSynchronous"
        context[SuperColliderClient].eval(code).thenAccept { answer ->
            val value = answer.toDoubleOrNull() ?: return@thenAccept
            control.knobControl.set(value)
        }
    }

    fun hasControl(name: String): Boolean = controls.any { ctrl -> ctrl.parameter == name }

    @Serializable
    class GlobalControl(val parameter: String, val knobControl: KnobControl, val spec: NumericalControlSpec) {
        val bus: BusObject = BusObject.create("global_$parameter", Rate.Control, 1)
    }
}