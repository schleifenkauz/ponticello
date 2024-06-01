package xenakis.model

import hextant.context.Context
import hextant.core.editor.ListenerManager
import javafx.geometry.HorizontalDirection
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
import xenakis.ui.format

class SynthObject(
    name: String,
    private val synthDefRef: SynthDefObject.Reference,
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
        _controls = controls.mapValuesTo(mutableMapOf()) { (_, c) -> c.cut(position, whichHalf) }
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

    override fun writeStartCode(writer: ScWriter, offset: Double, suffixGenerator: SuffixGenerator) {
        val name = "${name.now}${suffixGenerator.generateSuffix(this)}"
        writer.appendBlock("s.bind") {
            val synthVar = "~synth_${name}"
            +"$synthVar = Synth(\\${synthDef.name.now}, target: ${group.get().variableName})"
            for ((param, control) in controls) {
                when (control) {
                    is EnvelopeControl -> {
                        val env = control.envelope.code(offset)
                        val busName = "~auxil_${name}_${param}"
                        +"$busName = Bus.control(s, 1)"
                        +"{ $env }.play(s, $busName)"
                        +"${synthVar}.map(\\$param, $busName)"
                    }

                    is KnobControl -> {
                        val value = control.get().format(2)
                        +"${synthVar}.set(\\$param, $value)"
                    }

                    is BusControl -> {
                        val bus = control.bus.get().variableName
                        +"${synthVar}.set(\\$param, $bus)"
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
                        expr.code(this)
                        +" }.play(s, $busName)"
                        +"${synthVar}.map(\\$param, $busName)"
                    }

                    is BufferControl -> {
                        val buf = control.buffer.get().variableName
                        +"${synthVar}.set(\\$param, $buf)"
                    }

                    is ConstantControl -> +"${synthVar}.set(\\$param, ${control.value})"
                }
            }
        }
        writer.appendLine(";")
    }

    override fun writeStopCode(writer: ScWriter, suffixGenerator: SuffixGenerator) {
        val name = "${name.now}${suffixGenerator.getSuffix(this)}"
        with(writer) {
            val synthVar = "~synth_$name"
            +"$synthVar.free"
        }
    }

    override fun JsonObjectBuilder.saveToJson() {
        putSerializableValue("synthDef", synthDefRef)
        putSerializableValue("group", group)
        if (controls.isNotEmpty()) putSerializableValue<Map<String, ParameterControl>>("controls", controls)
    }

    object Serializer : ScoreObject.Serializer {
        override val type: String
            get() = "synth"

        override fun JsonObject.createFromJson(name: String): ScoreObject {
            val synthDef: SynthDefObject.Reference = getSerializableValue("synthDef")!!
            val group: GroupObjectReference = getSerializableValue("group")!!
            val controls = getSerializableValue<Map<String, ParameterControl>>("controls") ?: mapOf()
            return SynthObject(name, synthDef, group, controls.toMutableMap())
        }
    }
}