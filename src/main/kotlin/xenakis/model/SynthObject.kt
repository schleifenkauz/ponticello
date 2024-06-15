package xenakis.model

import hextant.context.Context
import hextant.core.editor.ListenerManager
import javafx.geometry.HorizontalDirection
import javafx.geometry.HorizontalDirection.RIGHT
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import reaktive.Observer
import reaktive.list.observeEach
import reaktive.value.now
import xenakis.impl.ScWriter
import xenakis.impl.getSerializableValue
import xenakis.impl.putSerializableValue
import xenakis.sc.ControlSpec
import xenakis.sc.editor.GroupSelector
import xenakis.ui.SynthObjectView

class SynthObject(
    name: String,
    private val synthDefRef: ObjectReference<SynthDefObject>,
    private val initialGroup: GroupObjectReference,
    private val _controls: MutableMap<String, ParameterControl>
) : RegularScoreObject(name) {
    private lateinit var parameterNameObserver: Observer
    override val type: String
        get() = "synth"

    override val viewManager: ListenerManager<SynthObjectView> = ListenerManager.createWeakListenerManager()

    lateinit var groupSelector: GroupSelector
        private set

    private val group: GroupObjectReference get() = if (initialized) groupSelector.result.now else initialGroup

    val synthDef: SynthDefObject get() = synthDefRef.get()

    val controls: Map<String, ParameterControl> get() = _controls

    var sample: SampleObject?
        get() = (controls["buf"] as? BufferControl)?.takeIf { ctrl -> ctrl.display }?.sample?.get()
        set(value) {
            check(sample != null) { "$this has no buffer control" }
            check(value != null) { "Attempt to set buffer of $this to null" }
            reassignControl("buf", BufferControl(value.createReference(), true))
        }

    var playbufStartPos: Double?
        get() = (controls["startPos"] as? ConstantControl)?.takeIf { sample != null }?.value
        set(value) {
            check(playbufStartPos != null) { "$this has no 'startPos' control" }
            check(value != null) { "Attempt to set startPos of $this to null" }
            reassignControl("startPos", ConstantControl(value))
        }

    var playBufRate: Double?
        get() = (controls["rate"] as? ConstantControl)?.takeIf { sample != null }?.value
        set(value) {
            check(playbufStartPos != null) { "$this has no 'rate' control" }
            check(value != null) { "Attempt to set rate of $this to null" }
            reassignControl("rate", ConstantControl(value))
        }

    fun reassignControl(parameter: String, control: ParameterControl) {
        if (parameter !in controls) error("Parameter $parameter not found on object $name")
        control.initialize(context)
        val oldControl = _controls[parameter]!!
        _controls[parameter] = control
        recordEdit(ScoreObjectEdit.ReassignControl(parameter, oldControl, control, this))
        viewManager.notifyListeners { reassignedControl(parameter, oldControl, control) }
    }

    fun addControl(parameter: String, control: ParameterControl) {
        if (synthDef.parameters.now.none { p -> p.name.now == parameter })
            error("Parameter $parameter not found on SynthDef for $name")
        control.initialize(context)
        _controls[parameter] = control
        recordEdit(ScoreObjectEdit.AddControl(parameter, control, this))
        viewManager.notifyListeners { addedControl(parameter, control) }
    }

    fun removeControl(parameter: String) {
        val control = _controls.remove(parameter) ?: error("Parameter $parameter not found on object $name")
        recordEdit(ScoreObjectEdit.RemoveControl(parameter, control, this))
        viewManager.notifyListeners { removedControl(parameter, control) }
    }

    override val associatedControls: Map<String, ParameterControl> get() = controls

    override fun copy(): ScoreObject = SynthObject(
        name.now, synthDefRef, group,
        _controls = controls.mapValuesTo(mutableMapOf()) { (_, c) -> c.copy() })

    override fun cut(position: Double, whichHalf: HorizontalDirection): ScoreObject = SynthObject(
        name.now, synthDefRef, initialGroup,
        _controls = controls.mapValuesTo(mutableMapOf()) { (name, c) ->
            when {
                name == "startPos" && c is ConstantControl && whichHalf == RIGHT ->
                    ConstantControl(c.value + position * (playBufRate ?: 1.0))

                else -> c.cut(position, whichHalf)
            }
        }
    )

    override fun getSpec(parameter: String): ControlSpec = synthDef.getParameter(parameter).spec.now

    override fun initialize(context: Context) {
        if (initialized) return
        super.initialize(context)
        synthDefRef.resolve(context)
        initialGroup.resolve(context)
        groupSelector = GroupSelector(context, initialGroup)
        for ((_, control) in controls) control.initialize(context)
        parameterNameObserver = synthDef.parameters.observeEach { _, p ->
            p.name.observe { _, oldName, newName ->
                val control = _controls[oldName] ?: return@observe
                removeControl(oldName)
                addControl(newName, control)
            }
        }
    }

    override fun writeStartCode(writer: ScWriter, offset: Double, name: String) {
        writer.appendBlock("s.makeBundle(0)") {
            val constantArguments = controls.mapNotNull { (param, control) ->
                when (control) {
                    is BufferControl -> param to (control.sample?.get()?.variableName ?: "0")
                    is BusControl -> param to control.bus.get().variableName
                    is ConstantControl -> {
                        val value = when (param) {
                            "startPos" -> control.value + offset
                            else -> control.value
                        }
                        param to value.toString()
                    }

                    is KnobControl -> param to control.get().toString()
                    else -> null
                }
            }.joinToString { (param, value) -> "$param: $value" }
            val synthVar = "~synths['$name']"
            val duration = "duration: ${duration - offset}"
            +"$synthVar = Synth(\\${synthDef.name.now}, [$constantArguments, $duration], target: ${group.get().variableName})"
            for ((param, control) in controls) {
                when (control) {
                    is EnvelopeControl -> {
                        val env = control.envelope.code(offset)
                        val busName = "~auxil_${name}_${param}"
                        +"$busName  = Bus.control(s, 1)"
                        +"{ $env }.play(s, $busName)"
                        +"${synthVar}.map(\\$param, $busName)"
                    }

                    is BusValueControl -> {
                        val bus = control.bus.get().variableName
                        +"${synthVar}.map(\\$param, $bus)"
                    }

                    is SingleBusValueControl -> {
                        val bus = control.bus.get().variableName
                        +"${synthVar}.set(\\$param, $bus.getSynchronized)"
                    }

                    is CustomControl -> {
                        val expr = control.expr
                        val busName = "~auxil_${name}_${param}"
                        +"$busName = Bus.control(s, 1)"
                        this.append("{ ")
                        expr.code(this, context)
                        +" }.play(s, $busName)"
                        +"${synthVar}.map(\\$param, $busName)"
                    }

                    else -> {} //already handled in constantArguments
                }
            }
        }
    }

    override fun JsonObjectBuilder.saveToJson() {
        putSerializableValue("synthDef", synthDefRef as InstrumentObject.Reference)
        putSerializableValue("group", group)
        if (controls.isNotEmpty()) putSerializableValue<Map<String, ParameterControl>>("controls", controls)
    }

    object Serializer : ScoreObject.Serializer {
        override val type: String
            get() = "synth"

        override fun JsonObject.createFromJson(name: String): ScoreObject {
            val synthDef = getSerializableValue<InstrumentObject.Reference>("synthDef")!!
            @Suppress("UNCHECKED_CAST")
            synthDef as ObjectReference<SynthDefObject>
            val group: GroupObjectReference = getSerializableValue("group")!!
            val controls = getSerializableValue<Map<String, ParameterControl>>("controls") ?: mapOf()
            return SynthObject(name, synthDef, group, controls.toMutableMap())
        }
    }
}