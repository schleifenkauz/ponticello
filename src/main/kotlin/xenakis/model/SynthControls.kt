package xenakis.model

import hextant.context.Context
import hextant.core.editor.ListenerManager
import hextant.undo.AbstractEdit
import hextant.undo.UndoManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.now

@Serializable
class SynthControls(private val map: MutableMap<String, ParameterControl>) {
    @Transient
    private lateinit var context: Context

    @Transient
    private lateinit var synthDef: SynthDefObject

    val controlMap: Map<String, ParameterControl> get() = map

    @Transient
    private val viewManager = ListenerManager.createWeakListenerManager<View>()

    fun initialize(context: Context, synthDefObject: SynthDefObject) {
        this.context = context
        synthDef = synthDefObject
        for ((_, ctrl) in map) ctrl.initialize(context)
    }

    operator fun get(name: String) = map.getValue(name)

    fun reassignControl(parameter: String, control: ParameterControl) {
        if (parameter !in map) error("Parameter $parameter not found in controls for ")
        control.initialize(context)
        val oldControl = map[parameter]!!
        map[parameter] = control
        context[UndoManager].record(Edit.ReassignControl(this, parameter, oldControl, control))
        viewManager.notifyListeners { reassignedControl(parameter, oldControl, control) }
    }

    fun addControl(parameter: String, control: ParameterControl) {
        if (synthDef.parameters.now.none { p -> p.name.now == parameter })
            error("Parameter $parameter not found in ${synthDef.name.now}")
        control.initialize(context)
        map[parameter] = control
        context[UndoManager].record(Edit.AddControl(this, parameter, control))
        viewManager.notifyListeners { addedControl(parameter, control) }
    }

    fun removeControl(parameter: String) {
        val control = map.remove(parameter) ?: error("Parameter $parameter not found in controls")
        context[UndoManager].record(Edit.RemoveControl(this, parameter, control))
        viewManager.notifyListeners { removedControl(parameter, control) }
    }

    fun transformControls(f: (String, ParameterControl) -> ParameterControl) =
        SynthControls(map.mapValuesTo(mutableMapOf()) { (name, ctrl) -> f(name, ctrl) })

    fun copy() = SynthControls(map.mapValuesTo(mutableMapOf()) { (_, c) -> c.copy() })

    fun addView(view: View) {
        for ((name, control) in controlMap) {
            view.addedControl(name, control)
        }
        viewManager.addListener(view)
    }

    abstract class Edit(protected val controls: SynthControls) : AbstractEdit() {
        class ReassignControl(
            controls: SynthControls,
            private val parameter: String,
            private val oldControl: ParameterControl,
            private val newControl: ParameterControl
        ) : Edit(controls) {
            override val actionDescription: String
                get() = "Reassign controls"

            override fun doUndo() {
                controls.reassignControl(parameter, oldControl)
            }

            override fun doRedo() {
                controls.reassignControl(parameter, newControl)
            }
        }

        class AddControl(
            controls: SynthControls,
            private val parameter: String,
            private val control: ParameterControl,
        ) : Edit(controls) {
            override val actionDescription: String
                get() = "Add control"

            override fun doUndo() {
                controls.addControl(parameter, control)
            }

            override fun doRedo() {
                controls.removeControl(parameter)
            }
        }

        class RemoveControl(
            controls: SynthControls,
            private val parameter: String,
            private val control: ParameterControl,
        ) : Edit(controls) {
            override val actionDescription: String
                get() = "Remove control"

            override fun doUndo() {
                controls.addControl(parameter, control)
            }

            override fun doRedo() {
                controls.removeControl(parameter)
            }
        }
    }

    interface View {
        fun addedControl(parameter: String, control: ParameterControl)
        fun removedControl(parameter: String, control: ParameterControl)
        fun reassignedControl(parameter: String, oldControl: ParameterControl, control: ParameterControl)
    }
}