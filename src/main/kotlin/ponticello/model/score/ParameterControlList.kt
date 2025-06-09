package ponticello.model.score

import fxutils.undo.UndoManager
import hextant.context.Context
import hextant.context.compoundEdit
import hextant.context.withoutUndo
import javafx.scene.input.DataFormat
import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.Logger
import ponticello.impl.asTime
import ponticello.impl.toDecimal
import ponticello.impl.zero
import ponticello.model.obj.*
import ponticello.model.registry.NamedObjectList
import ponticello.model.registry.ObjectList
import ponticello.model.registry.ObjectListSerializer
import ponticello.model.score.controls.BufferControl
import ponticello.model.score.controls.EnvelopeControl
import ponticello.model.score.controls.ExprControl
import ponticello.model.score.controls.ParameterControl
import ponticello.sc.BufferPositionControlSpec
import ponticello.sc.ControlSpec
import ponticello.sc.NumericalControlSpec
import ponticello.sc.Warp
import reaktive.Observer
import reaktive.value.*
import reaktive.value.binding.binding
import reaktive.value.binding.equalTo
import reaktive.value.binding.flatMap
import reaktive.value.binding.map

@Serializable(with = ParameterControlList.Serializer::class)
class ParameterControlList(
    override val objects: MutableList<NamedParameterControl> = mutableListOf(),
) : NamedObjectList<ParameterControlList.NamedParameterControl>(), ObjectList.Listener<ParameterDefObject> {
    override val objectType: String
        get() = "Parameter control"

    @Transient
    lateinit var associatedObject: ParameterizedObject
        private set

    @Transient
    private var invalidCount = reactiveVariable(0)

    val isValid: ReactiveValue<Boolean> get() = invalidCount.equalTo(0)

    @Transient
    private lateinit var validationObserver: Observer

    private val def: InstrumentObject get() = associatedObject.def

    private val parameterObservers = mutableMapOf<ParameterDefObject, Observer>()

    val controlMap: Map<String, ParameterControl> get() = associate { c -> c.name.now to c.now }

    @Serializable
    class NamedParameterControl(
        private val value: ReactiveVariable<ParameterControl>,
        private var customSpec: ControlSpec? = null,
    ) : AbstractRenamableObject() {
        constructor(value: ParameterControl, customSpec: ControlSpec? = null) : this(
            reactiveVariable(value), customSpec
        )

        @Transient
        private var _spec: ReactiveVariable<ControlSpec?> = reactiveVariable(null)

        @Transient
        private var specBinder: Observer? = null

        val spec: ReactiveValue<ControlSpec?> get() = _spec

        @Transient
        lateinit var controls: ParameterControlList
            private set

        @Transient
        lateinit var isValid: ReactiveBoolean
            private set

        val parentObject get() = controls.associatedObject

        val now get() = value.now

        fun copy(value: ParameterControl = now.copy()): NamedParameterControl =
            NamedParameterControl(value, customSpec).withName(name.now)

        override fun copy(): NamedParameterControl = copy(now.copy())

        override val canCopy: Boolean get() = true

        fun initialize(controls: ParameterControlList) {
            this.controls = controls
            super.initialize(controls.context)
            updateSpec(customSpec ?: parentObject.def.getSpec(name.now)?.now)
            value.now.initialize(context, this)
            isValid = binding(_spec, value) { spec, ctrl -> spec != null && ctrl.validate(spec, parentObject) }
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
                val buf = controls.controlMap.values.filterIsInstance<BufferControl>().firstOrNull()
                val duration = buf?.sample?.flatMap { s -> s.get()?.duration() ?: reactiveValue(zero) }
                duration?.map { dur ->
                    NumericalControlSpec(
                        default = zero, min = zero, max = dur,
                        step = 0.01.toDecimal(), lag = 0.01.asTime, warp = Warp.Linear,
                        associatedColor = Color.WHITE
                    ).also { it.origin = spec }
                } ?: reactiveValue(null)
            }

            else -> reactiveValue(spec)
        }

        fun setCustomSpec(custom: ControlSpec?) {
            val before = customSpec
            customSpec = custom
            if (initialized) {
                context[UndoManager].record(ParameterControlEdit.EditCustomSpec(this, before, custom))
                val defaultSpec = controls.getDefaultSpec(this)
                val newSpec = custom ?: defaultSpec
                updateSpec(newSpec)
            }
        }

        fun useSpecFromDefinition(): Boolean {
            check(spec.now == null) { "useSpecFromDefinition can only be used if current spec is null" }
            val spec = controls.def.getSpec(name.now) ?: return false
            specBinder?.kill()
            updateSpec(spec.now)

            return true
        }

        fun customSpec() = customSpec

        fun parameterNameChanged(newName: String) {
            context.withoutUndo {
                rename(newName)
            }
        }

        fun parameterSpecChanged(newSpec: ControlSpec) {
            if (customSpec == null) {
                val oldSpec = spec.now
                _spec.now = newSpec
                controls.notifyListeners<Listener> { changedSpec(this@NamedParameterControl, oldSpec, spec.now) }
            }
        }

        fun reassign(newControl: ParameterControl) {
            val oldControl = now
            newControl.initialize(context, this)
            value.now = newControl
            context[UndoManager].record(ParameterControlEdit.ReassignControl(this, oldControl, newControl))
            controls.notifyListeners<Listener> { reassignedControl(this@NamedParameterControl, oldControl, newControl) }
        }

        override fun canRenameTo(newName: String): Boolean = !controls.has(newName)

        companion object {
            val DATA_FORMAT = DataFormat("ponticello:named-parameter-control")
        }
    }

    fun initialize(context: Context, associatedObject: ParameterizedObject) {
        super.initialize(context)
        this.associatedObject = associatedObject
        for (ctrl in this) ctrl.initialize(this)
        def.parameters.addListener(this)
        setupValidation()
    }

    private fun setupValidation() {
        validationObserver = observeEach { ctrl ->
            if (!ctrl.isValid.now) invalidCount.now++
            ctrl.isValid.observe { _, _, valid ->
                if (valid) invalidCount.now--
                else invalidCount.now++
            }
        }
    }

    private fun getDefaultSpec(ctrl: NamedParameterControl) = def.getSpec(ctrl.name.now)?.now

    override fun added(obj: ParameterDefObject, idx: Int) {
        parameterObservers[obj] = obj.name.observe { _, oldName, newName ->
            val ctrl = getOrNull(oldName) ?: return@observe
            ctrl.parameterNameChanged(newName)
        } and obj.spec.observe { _, _, newSpec ->
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

    fun indexOf(parameter: String) = indexOfFirst { ctrl -> ctrl.name.now == parameter }

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
        val named = NamedParameterControl(control, customSpec).withName(parameter)
        add(named)
    }

    fun assignControl(parameter: String, control: ParameterControl) {
        if (has(parameter)) reassignControl(parameter, control)
        else addControl(parameter, control)
    }

    private fun canAcceptControl(control: NamedParameterControl): Boolean {
        return if (associatedObject !is ScoreObject) control.now !is EnvelopeControl && control.now !is ExprControl
        else true
    }

    fun duplicateControl(namedControl: NamedParameterControl) {
        if (!canAcceptControl(namedControl)) return
        val name = namedControl.name.now
        val control = namedControl.now
        val spec = namedControl.customSpec()
        if (control is EnvelopeControl) {
            val obj = associatedObject as ScoreObject
            control.points.rescale(obj.duration)
        }
        context.compoundEdit("Copy parameter control") {
            if (has(name)) {
                reassignControl(name, control)
                if (spec != null && spec != get(name).spec.now) {
                    get(name).setCustomSpec(spec)
                }
            } else {
                val customSpec = spec.takeIf { it != associatedObject.def.getSpec(name)?.now }
                addControl(name, control, customSpec)
            }
        }
    }

    override fun onAdded(obj: NamedParameterControl, idx: Int) {
        obj.initialize(this)
    }

    fun transformControls(f: (NamedParameterControl) -> ParameterControl) =
        ParameterControlList(mapTo(mutableListOf()) { p -> p.copy(f(p)) })

    fun copy() = ParameterControlList(mapTo(mutableListOf()) { ctrl -> ctrl.copy() })

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

    interface Listener : ObjectList.Listener<NamedParameterControl> {
        override fun added(obj: NamedParameterControl, idx: Int) {
        }

        override fun removed(obj: NamedParameterControl) {
        }

        fun reassignedControl(
            parameter: NamedParameterControl,
            oldControl: ParameterControl,
            newControl: ParameterControl,
        )

        fun changedSpec(parameter: NamedParameterControl, oldSpec: ControlSpec?, newSpec: ControlSpec?) {}
    }

    object Serializer : ObjectListSerializer<NamedParameterControl, ParameterControlList>(
        kotlinx.serialization.serializer(), ::ParameterControlList
    )

    companion object {
        fun empty() = ParameterControlList()

        fun create(vararg entries: Pair<String, ParameterControl>): ParameterControlList = from(entries.toMap())

        fun from(controls: Map<String, ParameterControl>): ParameterControlList =
            ParameterControlList(controls.mapTo(mutableListOf()) { (name, control) ->
                NamedParameterControl(control).withName(name)
            })
    }
}