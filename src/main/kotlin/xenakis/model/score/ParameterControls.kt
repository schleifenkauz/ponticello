package xenakis.model.score

import hextant.context.Context
import hextant.core.editor.ListenerManager
import hextant.undo.AbstractEdit
import hextant.undo.UndoManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.Observer
import reaktive.list.observeEach
import reaktive.value.now
import xenakis.model.obj.ParameterDefObject
import xenakis.model.obj.ParameterizedObjectDef
import xenakis.model.obj.SynthDefObject
import xenakis.sc.ControlSpec

@Serializable
class ParameterControls(
    private val map: MutableMap<String, ParameterControl>,
    private val extraSpecs: MutableMap<String, ControlSpec> = mutableMapOf()
) {
    val extraParameters: Collection<ParameterDefObject>
        get() = extraSpecs.map { (name, spec) -> ParameterDefObject(name, spec) }

    @Transient
    private lateinit var parameterNameObserver: Observer

    @Transient
    private lateinit var context: Context

    @Transient
    private lateinit var def: ParameterizedObjectDef

    val controlMap: MutableMap<String, ParameterControl> get() = map

    @Transient
    private val viewManager = ListenerManager.createWeakListenerManager<View>()

    fun initialize(context: Context, synthDefObject: SynthDefObject) {
        this.context = context
        def = synthDefObject
        for ((_, ctrl) in map) ctrl.initialize(context)
        parameterNameObserver = def.parameters.observeEach { _, p ->
            p.name.observe { _, oldName, newName ->
                val control = controlMap[oldName] ?: return@observe
                val extraSpec = getExtraSpec(oldName)
                if (extraSpec != null) {
                    extraSpecs.remove(oldName)
                    extraSpecs[newName] = extraSpec
                }
                removeControl(oldName)
                addControl(newName, control)
            } and p.spec.observe { _, _, newSpec ->
                if (p.name.now !in extraSpecs) {
                    viewManager.notifyListeners { changedSpec(p.name.now, newSpec) }
                }
            }
        }
    }

    operator fun get(name: String) = map.getValue(name)

    fun getExtraSpec(parameter: String): ControlSpec? = extraSpecs[parameter]

    fun reassignControl(parameter: String, control: ParameterControl) {
        if (parameter !in map) {
            addControl(parameter, control)
            return
        }
        val oldControl = map[parameter]!!
        control.initialize(context)
        map[parameter] = control
        context[UndoManager].record(Edit.ReassignControl(this, parameter, oldControl, control))
        viewManager.notifyListeners { reassignedControl(parameter, oldControl, control) }
    }

    fun addControl(parameter: String, control: ParameterControl) {
        control.initialize(context)
        map[parameter] = control
        context[UndoManager].record(Edit.AddControl(this, parameter, control))
        viewManager.notifyListeners { addedControl(parameter, control) }
    }

    fun setExtraSpec(parameter: String, spec: ControlSpec?) {
        val before = extraSpecs[parameter]
        if (before == spec) return
        if (spec == null || (before == null && spec == def.getParameter(parameter)?.spec)) {
            extraSpecs.remove(parameter)
        } else {
            extraSpecs[parameter] = spec
        }
        val after = extraSpecs[parameter]
        context[UndoManager].record(Edit.EditExtraSpec(this, parameter, before, after))
        val newSpec = after ?: def.getParameter(parameter)?.spec?.now
        ?: error("Cannot remove extra spec for parameter $parameter because is is not specified in $def")
        viewManager.notifyListeners { changedSpec(parameter, newSpec) }
    }

    fun removeExtraSpec(parameter: String) {
        val before = extraSpecs.remove(parameter)
        context[UndoManager].record(Edit.EditExtraSpec(this, parameter, before, extraSpecAfter = null))
        val defParameter = def.getParameter(parameter)!!
        val synthDefSpec = defParameter.spec.now
        viewManager.notifyListeners { changedSpec(parameter, synthDefSpec) }
    }

    fun removeControl(parameter: String) {
        val control = map.remove(parameter) ?: error("Parameter $parameter not found in controls")
        extraSpecs.remove(parameter)
        context[UndoManager].record(Edit.RemoveControl(this, parameter, control, extraSpecs[parameter]))
        viewManager.notifyListeners { removedControl(parameter, control) }
    }

    fun transformControls(f: (String, ParameterControl) -> ParameterControl) =
        ParameterControls(map.mapValuesTo(mutableMapOf()) { (name, ctrl) -> f(name, ctrl) }, extraSpecs.toMutableMap())

    fun copy() = ParameterControls(map.mapValuesTo(mutableMapOf()) { (_, c) -> c.copy() }, extraSpecs.toMutableMap())

    fun addView(view: View) {
        for ((name, control) in controlMap) {
            view.addedControl(name, control)
        }
        viewManager.addListener(view)
    }

    abstract class Edit(protected val controls: ParameterControls) : AbstractEdit() {
        class ReassignControl(
            controls: ParameterControls,
            private val parameter: String,
            private val oldControl: ParameterControl,
            private val newControl: ParameterControl,
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
            controls: ParameterControls,
            private val parameter: String,
            private val control: ParameterControl,
        ) : Edit(controls) {
            override val actionDescription: String
                get() = "Add control"

            override fun doUndo() {
                controls.removeControl(parameter)
            }

            override fun doRedo() {
                controls.addControl(parameter, control)
            }
        }

        class RemoveControl(
            controls: ParameterControls,
            private val parameter: String,
            private val control: ParameterControl,
            private val extraSpec: ControlSpec?,
        ) : Edit(controls) {
            override val actionDescription: String
                get() = "Remove control"

            override fun doUndo() {
                controls.setExtraSpec(parameter, extraSpec)
                controls.addControl(parameter, control)
            }

            override fun doRedo() {
                if (extraSpec != null) controls.setExtraSpec(parameter, null)
                controls.removeControl(parameter)
            }
        }

        class EditExtraSpec(
            controls: ParameterControls,
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

            override fun doUndo() {
                controls.setExtraSpec(parameter, extraSpecBefore)
            }

            override fun doRedo() {
                controls.setExtraSpec(parameter, extraSpecAfter)
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