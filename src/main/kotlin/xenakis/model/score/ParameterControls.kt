package xenakis.model.score

import hextant.context.Context
import hextant.core.editor.ListenerManager
import hextant.undo.AbstractEdit
import hextant.undo.UndoManager
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.Observer
import reaktive.value.*
import reaktive.value.binding.orElse
import xenakis.impl.Logger
import xenakis.model.obj.AbstractContextualObject
import xenakis.model.obj.AbstractRenamableObject
import xenakis.model.obj.ParameterizedObject
import xenakis.model.obj.ParameterizedObjectDef
import xenakis.sc.ControlSpec

@Serializable
class ParameterControls(private val controls: MutableList<NamedParameterControl>) : AbstractContextualObject() {
    @Transient
    private lateinit var associatedObject: ParameterizedObject

    private val def: ParameterizedObjectDef get() = associatedObject.def

    val controlMap: Map<String, ParameterControl> get() = controls.associate { c -> c.name.now to c.now }

    @Transient
    private val listenerManager = ListenerManager.createWeakListenerManager<Listener>()

    fun has(parameter: String): Boolean = controls.any { c -> c.name.now == parameter }

    @Serializable
    class NamedParameterControl(
        @SerialName("name") override val mutableName: ReactiveVariable<String>,
        private var value: ParameterControl,
        private val customSpec: ReactiveVariable<ControlSpec?> = reactiveVariable(null)
    ) : AbstractRenamableObject() {
        constructor(name: String, value: ParameterControl, customSpec: ControlSpec? = null) : this(
            reactiveVariable(name), value, reactiveVariable(customSpec)
        )

        @Transient
        lateinit var spec: ReactiveValue<ControlSpec?>
            private set

        @Transient
        lateinit var controls: ParameterControls
            private set

        val parentObject get() = controls.associatedObject

        fun customSpec(): ControlSpec? = customSpec.now

        @Transient
        private lateinit var observer: Observer

        val now get() = value

        override fun copy(name: String): NamedParameterControl = NamedParameterControl(name, value, customSpec.now)

        fun copy(value: ParameterControl = now): NamedParameterControl =
            NamedParameterControl(name.now, value, customSpec.now)

        override val canCopy: Boolean get() = true

        fun initialize(controls: ParameterControls) {
            this.controls = controls
            super.initialize(controls.context)
            value.initialize(context)
            spec = customSpec.orElse(controls.def.getSpec(name.now) ?: reactiveValue(null))
            observer = spec.observe { _, oldSpec, newSpec ->
                controls.notifyListeners { changedSpec(this@NamedParameterControl, oldSpec, newSpec) }
            }
        }

        fun setCustomSpec(custom: ControlSpec?) {
            val before = customSpec.now
            customSpec.set(custom)
            context[UndoManager].record(Edit.EditCustomSpec(this, before, custom))
        }

        fun reassign(newControl: ParameterControl) {
            val oldControl = value
            newControl.initialize(context)
            value = newControl
            context[UndoManager].record(Edit.ReassignControl(this, oldControl, newControl))
            controls.notifyListeners { reassignedControl(this@NamedParameterControl, oldControl, newControl) }
        }

        override fun canRenameTo(newName: String): Boolean = !controls.has(newName)

        override fun rename(newName: String) {
            val oldName = name.now
            super.rename(newName)
            controls.notifyListeners { renamedControl(this@NamedParameterControl, oldName, newName) }
        }
    }

    private fun notifyListeners(action: Listener.() -> Unit) {
        listenerManager.notifyListeners(action)
    }

    fun initialize(context: Context, associatedObject: ParameterizedObject) {
        super.initialize(context)
        this.associatedObject = associatedObject
        for (ctrl in controls) ctrl.initialize(this)
    }

    operator fun get(name: String) = controls.find { c -> c.name.now == name }

    fun getControl(name: String) = get(name)?.now

    fun getCustomSpec(parameter: String): ControlSpec? = get(parameter)?.customSpec()

    fun reassignControl(parameter: String, control: ParameterControl) {
        val named = get(parameter)
        if (named == null) {
            Logger.error("Parameter '$parameter' was not defined in $this")
            return
        }
        named.reassign(control)
    }

    fun addControl(parameter: String, control: ParameterControl, customSpec: ControlSpec? = null) {
        val named = NamedParameterControl(parameter, control, customSpec)
        addControl(named, idx = controls.size)
    }

    fun addControl(named: NamedParameterControl, idx: Int = controls.size) {
        controls.add(idx, named)
        context[UndoManager].record(Edit.AddControl(this, idx, named))
        listenerManager.notifyListeners { addedControl(named, idx) }
    }

    fun all(): List<NamedParameterControl> = controls

    fun removeControl(parameter: String) {
        val control = get(parameter) ?: error("Parameter $parameter not found in controls")
        removeControl(control)
    }

    fun removeControl(control: NamedParameterControl) {
        val idx = controls.indexOf(control)
        if (idx == -1) error("Parameter $control not found in $this")
        controls.remove(control)
        context[UndoManager].record(Edit.RemoveControl(this, idx, control))
        listenerManager.notifyListeners { removedControl(control) }
    }

    fun moveControl(control: NamedParameterControl, idx: Int) {
        val oldIdx = controls.indexOf(control)
        if (oldIdx == -1) error("Parameter $control not found in $this")
        if (oldIdx == idx) return
        controls.add(idx, control)
        controls.remove(control)
        context[UndoManager].record(Edit.MoveControl(this, control, oldIdx, idx))
        listenerManager.notifyListeners { movedControl(control, oldIdx, idx) }
    }

    fun transformControls(f: (String, ParameterControl) -> ParameterControl) =
        ParameterControls(controls.mapTo(mutableListOf()) { p -> p.copy(f(p.name.now, p.now)) })

    fun copy() = ParameterControls(controls.mapTo(mutableListOf()) { it.copy() })

    fun addListener(listener: Listener, initialize: Boolean = true) {
        listenerManager.addListener(listener)
        if (initialize) {
            for ((idx, control) in controls.withIndex()) {
                listener.addedControl(control, idx)
            }
        }
    }

    abstract class Edit(protected val controls: ParameterControls) : AbstractEdit() {
        class ReassignControl(
            private val control: NamedParameterControl,
            private val oldControl: ParameterControl,
            private val newControl: ParameterControl
        ) : AbstractEdit() {
            override val actionDescription: String
                get() = "Reassign controls"

            override fun doUndo() {
                control.reassign(oldControl)
            }

            override fun doRedo() {
                control.reassign(newControl)
            }
        }

        class AddControl(
            controls: ParameterControls,
            private val idx: Int,
            private val control: NamedParameterControl
        ) : Edit(controls) {
            override val actionDescription: String
                get() = "Add control"

            override fun doUndo() {
                controls.removeControl(control)
            }

            override fun doRedo() {
                controls.addControl(control, idx)
            }
        }

        class RemoveControl(
            controls: ParameterControls,
            private val index: Int,
            private val control: NamedParameterControl,
        ) : Edit(controls) {
            override val actionDescription: String
                get() = "Remove control"

            override fun doUndo() {
                controls.addControl(control, index)
            }

            override fun doRedo() {
                controls.removeControl(control)
            }
        }

        class MoveControl(
            controls: ParameterControls,
            private val control: NamedParameterControl,
            private val fromIdx: Int,
            private val toIdx: Int
        ) : Edit(controls) {
            override val actionDescription: String
                get() = "Move parameter control"

            override fun doRedo() {
                controls.moveControl(control, toIdx)
            }

            override fun doUndo() {
                controls.moveControl(control, fromIdx)
            }
        }

        class EditCustomSpec(
            private val control: NamedParameterControl,
            private val extraSpecBefore: ControlSpec?,
            private val extraSpecAfter: ControlSpec?
        ) : AbstractEdit() {
            override val actionDescription: String
                get() = when {
                    extraSpecBefore != null && extraSpecAfter == null -> "Reset parameter spec"
                    extraSpecBefore == null && extraSpecAfter != null -> "Add custom parameter spec"
                    else -> "Modify extra spec"
                }

            override fun doUndo() {
                control.setCustomSpec(extraSpecBefore)
            }

            override fun doRedo() {
                control.setCustomSpec(extraSpecAfter)
            }
        }

    }

    interface Listener {
        fun renamedControl(controls: NamedParameterControl, oldName: String, newName: String) {
        }

        fun addedControl(control: NamedParameterControl, idx: Int)

        fun removedControl(control: NamedParameterControl)

        fun movedControl(control: NamedParameterControl, fromIdx: Int, toIdx: Int) {}

        fun reassignedControl(
            namedControl: NamedParameterControl,
            oldControl: ParameterControl,
            control: ParameterControl
        )

        fun changedSpec(control: NamedParameterControl, oldSpec: ControlSpec?, newSpec: ControlSpec?) {}
    }

    companion object {
        fun empty() = ParameterControls(mutableListOf())

        fun create(vararg entries: Pair<String, ParameterControl>): ParameterControls = from(entries.asList())

        fun from(controls: List<Pair<String, ParameterControl>>): ParameterControls {
            val controls = controls.mapTo(mutableListOf()) { (name, control) -> NamedParameterControl(name, control) }
            return ParameterControls(controls)
        }
    }
}