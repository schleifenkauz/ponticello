package xenakis.model.score

import hextant.context.Context
import hextant.undo.AbstractEdit
import hextant.undo.UndoManager
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.Observer
import reaktive.value.*
import reaktive.value.binding.orElse
import xenakis.impl.Logger
import xenakis.model.obj.AbstractRenamableObject
import xenakis.model.obj.ParameterizedObject
import xenakis.model.obj.ParameterizedObjectDef
import xenakis.model.registry.NamedObjectList
import xenakis.model.registry.NamedObjectListSerializer
import xenakis.sc.ControlSpec

@Serializable(with = ParameterControls.Serializer::class)
class ParameterControls(
    override val objects: MutableList<NamedParameterControl> = mutableListOf(),
) : NamedObjectList<ParameterControls.NamedParameterControl>(),
    List<ParameterControls.NamedParameterControl> by objects {
    override val objectType: String
        get() = "Parameter control"

    @Transient
    private lateinit var associatedObject: ParameterizedObject

    private val def: ParameterizedObjectDef get() = associatedObject.def

    val controlMap: Map<String, ParameterControl> get() = associate { c -> c.name.now to c.now }

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
                controls.notifyListeners<Listener> { changedSpec(this@NamedParameterControl, oldSpec, newSpec) }
            }
        }

        fun setCustomSpec(custom: ControlSpec?) {
            val before = customSpec.now
            customSpec.set(custom)
            context[UndoManager].record(EditCustomSpec(this, before, custom))
        }

        fun reassign(newControl: ParameterControl) {
            val oldControl = value
            newControl.initialize(context)
            value = newControl
            context[UndoManager].record(ReassignControl(this, oldControl, newControl))
            controls.notifyListeners<Listener> { reassignedControl(this@NamedParameterControl, oldControl, newControl) }
        }

        override fun canRenameTo(newName: String): Boolean = !controls.has(newName)
    }

    fun initialize(context: Context, associatedObject: ParameterizedObject) {
        super.initialize(context)
        this.associatedObject = associatedObject
        for (ctrl in this) ctrl.initialize(this)
    }

    fun getControl(name: String) = getOrNull(name)?.now

    fun getCustomSpec(parameter: String): ControlSpec? = getOrNull(parameter)?.customSpec()

    fun reassignControl(parameter: String, control: ParameterControl) {
        val named = getOrNull(parameter)
        if (named == null) {
            Logger.error("Parameter '$parameter' was not defined in $this")
            return
        }
        named.reassign(control)
    }

    fun addControl(parameter: String, control: ParameterControl, customSpec: ControlSpec? = null) {
        val named = NamedParameterControl(parameter, control, customSpec)
        add(named)
    }

    fun transformControls(f: (String, ParameterControl) -> ParameterControl) =
        ParameterControls(mapTo(mutableListOf()) { p -> p.copy(f(p.name.now, p.now)) })

    fun copy() = ParameterControls(mapTo(mutableListOf()) { it.copy() })

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

    interface Listener : NamedObjectList.Listener<NamedParameterControl> {
        override fun added(obj: NamedParameterControl, idx: Int) {
        }

        override fun removed(obj: NamedParameterControl) {
        }

        fun reassignedControl(
            namedControl: NamedParameterControl,
            oldControl: ParameterControl,
            control: ParameterControl
        )

        fun changedSpec(control: NamedParameterControl, oldSpec: ControlSpec?, newSpec: ControlSpec?) {}
    }

    object Serializer : NamedObjectListSerializer<NamedParameterControl, ParameterControls>(
        kotlinx.serialization.serializer(), ::ParameterControls
    )

    companion object {
        fun empty() = ParameterControls()

        fun create(vararg entries: Pair<String, ParameterControl>): ParameterControls = from(entries.asList())

        fun from(controls: List<Pair<String, ParameterControl>>): ParameterControls =
            ParameterControls(controls.mapTo(mutableListOf<NamedParameterControl>()) { (name, control) ->
                NamedParameterControl(name, control)
            })
    }
}