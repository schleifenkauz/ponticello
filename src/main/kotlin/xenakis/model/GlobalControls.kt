package xenakis.model

import hextant.context.Context
import hextant.core.editor.ViewManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import xenakis.impl.SuperColliderContext
import xenakis.impl.UDPSuperColliderClient
import xenakis.sc.Bus
import xenakis.sc.NumericalControlSpec
import xenakis.sc.Rate
import xenakis.ui.KnobControlView
import xenakis.ui.format

@Serializable
class GlobalControls(private val controls: MutableList<GlobalControl>) : KnobControlView {
    @Transient
    val views = ViewManager.createWeakViewManager<GlobalControlsView>()

    @Transient
    private lateinit var context: Context

    fun initialize(context: Context) {
        this.context = context
        for (ctrl in controls) {
            ctrl.knobControl.addView(this)
        }
    }

    val busses get() = controls.map { ctrl -> ctrl.bus }

    fun setupBusses(context: SuperColliderContext) {
        for (control in controls) {
            setupBus(control, context)
        }
    }

    private fun setupBus(control: GlobalControl, context: SuperColliderContext) {
        val bus = control.bus
        val varName = bus.variableName
        val value = control.knobControl.get()
        context.postAsync("${bus.allocationCode}; $varName.set($value);")
    }

    private fun removeBus(control: GlobalControl) {
        val bus = control.bus
        context[UDPSuperColliderClient].postAsync("${bus.variableName}.free;")
        control.knobControl.views.removeView(this)
    }

    fun addControl(parameter: String, spec: NumericalControlSpec) {
        val knobControl = KnobControl(parameter, spec.defaultValue.value)
        val control = GlobalControl(knobControl, spec)
        controls.add(control)
        setupBus(control, context[UDPSuperColliderClient])
        views.notifyViews { addedControl(control) }
    }

    fun removeControl(control: GlobalControl) {
        controls.remove(control)
        views.notifyViews { removedControl(control) }
        removeBus(control)
    }

    fun addView(view: GlobalControlsView) {
        views.addView(view)
        for (control in controls) {
            view.addedControl(control)
        }
    }

    override fun updatedValue(control: KnobControl, value: Double) {
        val ctrl = controls.find { it.knobControl == control }
            ?: error("${control.parameter} not found in global controls")
        val bus = ctrl.bus
        val formatted = value.format(ctrl.spec.accuracy)
        context[UDPSuperColliderClient].postAsync("if (${bus.variableName} != nil) { ${bus.variableName}.setSynchronous($formatted) };")
    }

    fun updateControlFromServer(control: GlobalControl) {
        val code = "${control.bus.variableName}.getSynchronous"
        val answer = context[UDPSuperColliderClient].post(code)
        val value = answer.toDoubleOrNull() ?: return
        control.knobControl.set(value)
    }

    @Serializable
    class GlobalControl(val knobControl: KnobControl, val spec: NumericalControlSpec) {
        val bus: Bus
            get() {
                val param = knobControl.parameter
                val busName = "global_$param"
                val bus = Bus(busName, Rate.Control, 1)
                return bus
            }

    }
}