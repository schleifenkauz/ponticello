package xenakis.model

import hextant.context.Context
import hextant.core.editor.ListenerManager
import hextant.undo.AbstractEdit
import hextant.undo.UndoManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.now
import xenakis.sc.ControlSpec

@Serializable
class SynthControls(
    private val map: MutableMap<String, ParameterControl>,
    private val extraSpecs: MutableMap<String, ControlSpec> = mutableMapOf()
) {
    val extraParameters: Collection<ParameterDefObject>
        get() = extraSpecs.map { (name, spec) -> ParameterDefObject(name, spec) }

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

    fun getExtraSpec(parameter: String): ControlSpec? = extraSpecs[parameter]

    fun reassignControl(parameter: String, control: ParameterControl, extraSpec: ControlSpec? = null) {
        if (parameter !in map) {
            addControl(parameter, control, extraSpec)
            return
        }
        val oldControl = map[parameter]!!
        doAddControl(control, parameter, extraSpec)
        context[UndoManager].record(Edit.ReassignControl(this, parameter, oldControl, control))
        viewManager.notifyListeners { reassignedControl(parameter, oldControl, control) }
    }

    fun addControl(parameter: String, control: ParameterControl, extraSpec: ControlSpec?) {
        doAddControl(control, parameter, extraSpec)
        context[UndoManager].record(Edit.AddControl(this, parameter, control, extraSpec))
        viewManager.notifyListeners { addedControl(parameter, control) }
    }

    fun setExtraSpec(parameter: String, spec: ControlSpec) {
        val before = extraSpecs[parameter]
        extraSpecs[parameter] = spec
        if (before == null && spec == synthDef.getParameter(parameter)?.spec) {
            return
        }
        context[UndoManager].record(Edit.EditExtraSpec(this, parameter, before, spec))
        viewManager.notifyListeners { changedSpec(parameter, spec) }
    }

    fun removeExtraSpec(parameter: String) {
        val before = extraSpecs.remove(parameter)
        context[UndoManager].record(Edit.EditExtraSpec(this, parameter, before, extraSpecAfter = null))
        val defParameter = synthDef.getParameter(parameter)!!
        val synthDefSpec = defParameter.spec.now
        viewManager.notifyListeners { changedSpec(parameter, synthDefSpec) }
    }

    private fun doAddControl(control: ParameterControl, parameter: String, extraSpec: ControlSpec?) {
        control.initialize(context)
        map[parameter] = control
        if (extraSpec != null) extraSpecs[parameter] = extraSpec
    }

    fun removeControl(parameter: String) {
        val control = map.remove(parameter) ?: error("Parameter $parameter not found in controls")
        extraSpecs.remove(parameter)
        context[UndoManager].record(Edit.RemoveControl(this, parameter, control, extraSpecs[parameter]))
        viewManager.notifyListeners { removedControl(parameter, control) }
    }

    fun transformControls(f: (String, ParameterControl) -> ParameterControl) =
        SynthControls(map.mapValuesTo(mutableMapOf()) { (name, ctrl) -> f(name, ctrl) }, extraSpecs.toMutableMap())

    fun copy() = SynthControls(map.mapValuesTo(mutableMapOf()) { (_, c) -> c.copy() }, extraSpecs.toMutableMap())

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
            private val extraSpec: ControlSpec?,
        ) : Edit(controls) {
            override val actionDescription: String
                get() = "Add control"

            override fun doUndo() {
                controls.removeControl(parameter)
            }

            override fun doRedo() {
                controls.addControl(parameter, control, extraSpec)
            }
        }

        class RemoveControl(
            controls: SynthControls,
            private val parameter: String,
            private val control: ParameterControl,
            private val extraSpec: ControlSpec?,
        ) : Edit(controls) {
            override val actionDescription: String
                get() = "Remove control"

            override fun doUndo() {
                controls.addControl(parameter, control, extraSpec)
            }

            override fun doRedo() {
                controls.removeControl(parameter)
            }
        }

        class EditExtraSpec(
            controls: SynthControls,
            private val parameter: String,
            private val extraSpecBefore: ControlSpec?,
            private val extraSpecAfter: ControlSpec?
        ) : Edit(controls) {
            override val actionDescription: String
                get() = when {
                    extraSpecBefore != null && extraSpecAfter == null -> "Reset parameter spec"
                    extraSpecBefore == null && extraSpecAfter != null -> "Add extra parameter spec"
                    else -> "Modify extra spec"
                }

            override fun doRedo() {
                if (extraSpecAfter != null) controls.setExtraSpec(parameter, extraSpecAfter)
                else controls.removeExtraSpec(parameter)
            }

            override fun doUndo() {
                if (extraSpecBefore != null) controls.setExtraSpec(parameter, extraSpecBefore)
                else controls.removeExtraSpec(parameter)
            }
        }
    }

    interface View {
        fun addedControl(parameter: String, control: ParameterControl)
        fun removedControl(parameter: String, control: ParameterControl)
        fun reassignedControl(parameter: String, oldControl: ParameterControl, control: ParameterControl) {
            removedControl(parameter, oldControl)
            addedControl(parameter, control)
        }
        fun changedSpec(parameter: String, newSpec: ControlSpec) {}
    }
}