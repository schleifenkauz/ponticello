package xenakis.model.score

import hextant.context.Context
import hextant.undo.AbstractEdit
import hextant.undo.UndoManager
import javafx.scene.paint.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.Observer
import reaktive.value.*
import reaktive.value.binding.flatMap
import reaktive.value.binding.map
import xenakis.impl.Logger
import xenakis.impl.toDecimal
import xenakis.impl.zero
import xenakis.model.obj.AbstractRenamableObject
import xenakis.model.obj.ParameterDefObject
import xenakis.model.obj.ParameterizedObject
import xenakis.model.obj.ParameterizedObjectDef
import xenakis.model.registry.NamedObjectList
import xenakis.model.registry.NamedObjectListSerializer
import xenakis.model.score.controls.BufferControl
import xenakis.model.score.controls.ParameterControl
import xenakis.sc.BufferPositionControlSpec
import xenakis.sc.ControlSpec
import xenakis.sc.NumericalControlSpec
import xenakis.sc.Warp

@Serializable(with = ParameterControlList.Serializer::class)
class ParameterControlList(
    override val objects: MutableList<NamedParameterControl> = mutableListOf(),
) : NamedObjectList<ParameterControlList.NamedParameterControl>(), NamedObjectList.Listener<ParameterDefObject> {
    override val objectType: String
        get() = "Parameter control"

    @Transient
    lateinit var associatedObject: ParameterizedObject
        private set

    private val def: ParameterizedObjectDef get() = associatedObject.def

    private val parameterObservers = mutableMapOf<ParameterDefObject, Observer>()

    val controlMap: Map<String, ParameterControl> get() = associate { c -> c.name.now to c.now }

    @Serializable
    class NamedParameterControl(
        @SerialName("name") override val mutableName: ReactiveVariable<String>,
        private var value: ParameterControl,
        private var customSpec: ControlSpec? = null,
    ) : AbstractRenamableObject() {
        constructor(name: String, value: ParameterControl, customSpec: ControlSpec? = null) : this(
            reactiveVariable(name), value, customSpec
        )

        @Transient
        private var _spec: ReactiveVariable<ControlSpec?> = reactiveVariable(null)

        @Transient
        private var specBinder: Observer? = null

        val spec: ReactiveValue<ControlSpec?> get() = _spec

        @Transient
        lateinit var controls: ParameterControlList
            private set

        val parentObject get() = controls.associatedObject

        val now get() = value

        override fun copy(name: String): NamedParameterControl = NamedParameterControl(name, value, customSpec)

        fun copy(value: ParameterControl = now.copy()): NamedParameterControl =
            NamedParameterControl(name.now, value, customSpec)

        override val canCopy: Boolean get() = true

        fun initialize(controls: ParameterControlList) {
            this.controls = controls
            super.initialize(controls.context)
            value.initialize(context)
            val spec = customSpec ?: parentObject.def.getSpec(name.now)?.now
            updateSpec(spec)
        }

        private fun updateSpec(spec: ControlSpec?) {
            specBinder?.tryKill()
            specBinder = resolveControlSpec(spec).forEach { s ->
                val oldSpec = _spec.now
                _spec.now = s
                controls.notifyListeners<Listener> { changedSpec(this@NamedParameterControl, oldSpec, s) }
            }
        }

        private fun resolveControlSpec(spec: ControlSpec?): ReactiveValue<ControlSpec?> = when (spec) {
            null -> reactiveValue(null)
            is BufferPositionControlSpec -> {
                val buf = controls.getOrNull("buf")?.value as? BufferControl
                val duration = buf?.sample?.flatMap { s -> s.get()?.duration() ?: reactiveValue(zero) }
                duration?.map { dur ->
                    NumericalControlSpec(
                        default = zero, min = zero, max = dur,
                        step = 0.01.toDecimal(), warp = Warp.Linear,
                        associatedColor = Color.WHITE
                    )
                } ?: reactiveValue(null)
            }

            else -> reactiveValue(spec)
        }

        fun setCustomSpec(custom: ControlSpec?) {
            val before = customSpec
            customSpec = custom
            context[UndoManager].record(EditCustomSpec(this, before, custom))
            val defaultSpec = controls.getDefaultSpec(this)
            val newSpec = custom ?: defaultSpec
            updateSpec(newSpec)
        }

        fun useSpecFromDefinition(): Boolean {
            check(spec.now == null) { "useSpecFromDefinition can only be used if current spec is null" }
            val spec = controls.def.getSpec(name.now) ?: return false
            specBinder?.kill()
            _spec.bind(resolveControlSpec(spec.now))

            return true
        }

        fun customSpec() = customSpec

        fun parameterNameChanged(newName: String) {
            mutableName.now = newName
        }

        fun parameterSpecChanged(newSpec: ControlSpec) {
            if (customSpec == null) {
                val oldSpec = spec.now
                _spec.now = newSpec
                controls.notifyListeners<Listener> { changedSpec(this@NamedParameterControl, oldSpec, spec.now) }
            }
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
        def.parameters.addListener(this)
    }

    private fun getDefaultSpec(ctrl: NamedParameterControl) = def.getSpec(ctrl.name.now)?.now

    override fun added(obj: ParameterDefObject, idx: Int) {
        parameterObservers[obj] = obj.name.observe { _, oldName, newName ->
            val ctrl = getOrNull(oldName) ?: return@observe
            ctrl.parameterNameChanged(newName)
        } and obj.spec.observe { _, oldSpec, newSpec ->
            val ctrl = getOrNull(obj.name.now) ?: return@observe
            ctrl.parameterSpecChanged(newSpec)
        }
        val ctrl = getOrNull(obj.name.now) ?: return
        if (ctrl.customSpec() == obj.spec.now) {
            ctrl.setCustomSpec(null)
        }
    }

    override fun removed(obj: ParameterDefObject) {
        parameterObservers.remove(obj)!!.kill()
        val ctrl = getOrNull(obj.name.now) ?: return
        if (ctrl.customSpec() == null) {
            ctrl.setCustomSpec(obj.spec.now) //remember spec
        }
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

    override fun onAdded(obj: NamedParameterControl, idx: Int) {
        obj.initialize(this)
    }

    fun transformControls(f: (NamedParameterControl) -> ParameterControl) =
        ParameterControlList(mapTo(mutableListOf()) { p -> p.copy(f(p)) })

    fun copy() = ParameterControlList(mapTo(mutableListOf()) { it.copy() })

    fun validate(): Boolean {
        var valid = true
        for (control in this) {
            val spec = control.spec.now
            if (spec == null) {
                Logger.error("No spec found for control ${control.name.now} on $associatedObject")
                valid = false
                continue
            }
            if (!control.now.validate(spec, associatedObject)) {
                valid = false
            }
        }
        if (!valid) {
            Logger.error("Validation on $associatedObject")
        }
        return valid
    }

    class ReassignControl(
        private val control: NamedParameterControl,
        private val oldControl: ParameterControl,
        private val newControl: ParameterControl,
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
        private val extraSpecAfter: ControlSpec?,
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
            control: ParameterControl,
        )

        fun changedSpec(control: NamedParameterControl, oldSpec: ControlSpec?, newSpec: ControlSpec?) {}
    }

    object Serializer : NamedObjectListSerializer<NamedParameterControl, ParameterControlList>(
        kotlinx.serialization.serializer(), ::ParameterControlList
    )

    companion object {
        fun empty() = ParameterControlList()

        fun create(vararg entries: Pair<String, ParameterControl>): ParameterControlList = from(entries.asList())

        fun from(controls: List<Pair<String, ParameterControl>>): ParameterControlList =
            ParameterControlList(controls.mapTo(mutableListOf<NamedParameterControl>()) { (name, control) ->
                NamedParameterControl(name, control)
            })
    }
}