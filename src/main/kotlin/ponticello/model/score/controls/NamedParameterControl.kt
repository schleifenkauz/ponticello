package ponticello.model.score.controls

import fxutils.drag.TypedDataFormat
import fxutils.undo.UndoManager
import hextant.context.withoutUndo
import hextant.core.editor.ListenerManager
import hextant.core.editor.notifyListeners
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.asTime
import ponticello.impl.toDecimal
import ponticello.impl.zero
import ponticello.model.obj.AbstractRenamableObject
import ponticello.model.obj.withName
import ponticello.model.registry.ObjectList
import ponticello.sc.BufferPositionControlSpec
import ponticello.sc.ControlSpec
import ponticello.sc.NumericalControlSpec
import ponticello.sc.Warp
import reaktive.Observer
import reaktive.value.*
import reaktive.value.binding.binding
import reaktive.value.binding.flatMap
import reaktive.value.binding.map

@Serializable
class NamedParameterControl(
    private val value: ReactiveVariable<ParameterControl>,
    private var customSpec: ControlSpec? = null,
) : AbstractRenamableObject(), java.io.Serializable {
    @SerialName("name")
    override var _name: ReactiveVariable<String>? = null

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
    private lateinit var listeners: ListenerManager<out ObjectList.Listener<NamedParameterControl>>

    @Transient
    lateinit var isValid: ReactiveBoolean
        private set

    val parentObject get() = controls.associatedObject

    fun value(): ReactiveValue<ParameterControl> = value

    val now get() = value.now

    fun copy(value: ParameterControl = now.copy()): NamedParameterControl =
        NamedParameterControl(value, customSpec).withName(name.now)

    override fun copy(): NamedParameterControl = copy(now.copy())

    fun initialize(
        controls: ParameterControlList,
        listeners: ListenerManager<out ObjectList.Listener<NamedParameterControl>>
    ) {
        this.controls = controls
        this.listeners = listeners
        updateSpec(customSpec ?: parentObject.getInstrument().getSpec(name.now)?.now)
        value.now.initialize(context, this)
        isValid = binding(_spec, value) { spec, ctrl -> spec != null && ctrl.validate(spec, parentObject) }
    }

    private fun updateSpec(spec: ControlSpec?) {
        specBinder?.tryKill()
        specBinder = resolveControlSpec(spec).forEach { s ->
            val oldSpec = _spec.now
            _spec.now = s
            listeners.notifyListeners<ParameterControlList.Listener> {
                changedSpec(
                    this@NamedParameterControl,
                    oldSpec,
                    s
                )
            }
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
                    step = 0.01.toDecimal(), warp = Warp.Linear, lag = 0.01.asTime
                ).also { it.origin = spec }
            } ?: reactiveValue(null)
        }

        else -> reactiveValue(spec)
    }

    fun setCustomSpec(custom: ControlSpec?) {
        val before = customSpec
        customSpec = custom
        if (initialized) {
            context[UndoManager.Companion].record(ParameterControlEdit.EditCustomSpec(this, before, custom))
            val instr = controls.associatedObject.getInstrument()
            val defaultSpec = instr.getSpec(name.now)?.now
            val newSpec = custom ?: defaultSpec
            updateSpec(newSpec)
        }
    }

    fun useSpecFromDefinition(): Boolean {
        check(spec.now == null) { "useSpecFromDefinition can only be used if current spec is null" }
        val instr = controls.associatedObject.getInstrument()
        val spec = instr.getSpec(name.now) ?: return false
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
            listeners.notifyListeners<ParameterControlList.Listener> {
                changedSpec(
                    this@NamedParameterControl,
                    oldSpec,
                    spec.now
                )
            }
        }
    }

    fun reassign(newControl: ParameterControl) {
        val oldControl = now
        newControl.initialize(context, this)
        value.now = newControl
        context[UndoManager.Companion].record(ParameterControlEdit.ReassignControl(this, oldControl, newControl))
        listeners.notifyListeners<ParameterControlList.Listener> {
            reassignedControl(
                this@NamedParameterControl,
                oldControl,
                newControl
            )
        }
    }

    override fun onRename(oldName: String, newName: String) {
        listeners.notifyListeners<ParameterControlList.Listener> {
            renamedControl(this@NamedParameterControl, oldName, newName)
        }
    }

    override fun canRenameTo(newName: String): Boolean = !controls.has(newName)

    companion object {
        val DATA_FORMAT = TypedDataFormat<NamedParameterControl>("ponticello:named-parameter-control")
    }
}