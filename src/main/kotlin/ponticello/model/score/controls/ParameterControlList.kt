package ponticello.model.score.controls

import hextant.context.Context
import hextant.context.compoundEdit
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.Logger
import ponticello.model.instr.ParameterDefObject
import ponticello.model.instr.ParameterizedObject
import ponticello.model.obj.withName
import ponticello.model.registry.NamedObjectList
import ponticello.model.registry.ObjectList
import ponticello.model.registry.ObjectListSerializer
import ponticello.model.score.ScoreObject
import ponticello.sc.ControlSpec
import reaktive.Observer
import reaktive.and
import reaktive.value.ReactiveValue
import reaktive.value.binding.equalTo
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable(with = ParameterControlList.Serializer::class)
class ParameterControlList(
    override val objects: MutableList<NamedParameterControl> = mutableListOf(),
) : NamedObjectList<NamedParameterControl>(), ObjectList.Listener<ParameterDefObject> {
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

    private val parameterObservers = mutableMapOf<ParameterDefObject, Observer>()

    val controlMap: Map<String, ParameterControl> get() = associate { c -> c.name.now to c.now }

    fun initialize(context: Context, associatedObject: ParameterizedObject) {
        super.initialize(context)
        this.associatedObject = associatedObject
        for (ctrl in this.toList()) ctrl.initialize(this, listeners)
        val def = associatedObject.getInstrument()
        val parameters = def.parameters
        if (parameters is ObjectList) {
            parameters.addListener(this)
        }
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

    override fun removed(obj: ParameterDefObject, idx: Int) {
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

    fun addControl(
        parameter: String, control: ParameterControl, customSpec: ControlSpec? = null,
        idx: Int = objects.size,
    ) {
        val named = NamedParameterControl(control, customSpec).withName(parameter)
        add(named, idx)
    }

    fun assignControl(parameter: String, control: ParameterControl) {
        if (has(parameter)) reassignControl(parameter, control)
        else addControl(parameter, control)
    }

    private fun canAcceptControl(control: NamedParameterControl): Boolean {
        return if (associatedObject !is ScoreObject) control.now !is EnvelopeControl && control.now !is ExprControl
        else true
    }

    fun duplicateControl(namedControl: NamedParameterControl, idx: Int = objects.size) {
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
                if (idx != objects.size) {
                    move(get(name), idx)
                }
            } else {
                val customSpec = spec.takeIf { it != associatedObject.getInstrument().getSpec(name)?.now }
                addControl(name, control, customSpec, idx)
            }
        }
    }

    override fun onAdded(obj: NamedParameterControl, idx: Int) {
        obj.initialize(this, listeners)
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

        override fun removed(obj: NamedParameterControl, idx: Int) {
        }

        fun reassignedControl(
            parameter: NamedParameterControl, oldControl: ParameterControl, newControl: ParameterControl,
        )

        fun changedSpec(parameter: NamedParameterControl, oldSpec: ControlSpec?, newSpec: ControlSpec?) {}

        fun renamedControl(parameter: NamedParameterControl, oldName: String, newName: String) {}
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