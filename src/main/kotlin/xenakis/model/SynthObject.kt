package xenakis.model

import hextant.context.Context
import hextant.core.editor.ListenerManager
import javafx.geometry.HorizontalDirection
import javafx.geometry.HorizontalDirection.RIGHT
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import reaktive.Observer
import reaktive.list.observeEach
import reaktive.value.*
import xenakis.impl.ScWriter
import xenakis.impl.SuperColliderClient
import xenakis.impl.getSerializableValue
import xenakis.impl.putSerializableValue
import xenakis.sc.ControlSpec
import xenakis.sc.GroupControlSpec
import xenakis.ui.SynthObjectView

class SynthObject(
    name: String,
    private val synthDefRef: ObjectReference<SynthDefObject>,
    val controls: SynthControls
) : RegularScoreObject(name), SynthControls.View {
    private lateinit var parameterNameObserver: Observer
    override val type: String
        get() = "synth"

    override val viewManager: ListenerManager<SynthObjectView> = ListenerManager.createWeakListenerManager()

    private val controlObservers = mutableMapOf<ParameterControl, Observer>()
    private val activeSynths = mutableSetOf<String>()

    val synthDef: SynthDefObject get() = synthDefRef.get()

    val group: ReactiveValue<GroupObjectReference> get() = (controls["group"] as GroupControl).group

    private val bufferControl get() = controls.controlMap["buf"] as? BufferControl

    val sample: ReactiveValue<SampleObjectReference?> get() = bufferControl?.sample ?: reactiveVariable(null)

    val displaySample: ReactiveValue<Boolean>? get() = bufferControl?.display

    val playbufStartPos: ReactiveVariable<Double>?
        get() = (controls["startPos"] as? ConstantControl)?.value

    val playBufRate: ReactiveVariable<Double>?
        get() = (controls["rate"] as? ConstantControl)?.value

    override val associatedControls: Map<String, ParameterControl> get() = controls.controlMap

    override fun copy(): ScoreObject = SynthObject(
        name.now, synthDefRef,
        controls = controls.copy()
    )

    override fun cut(position: Double, whichHalf: HorizontalDirection): ScoreObject = SynthObject(
        name.now, synthDefRef,
        controls = controls.transformControls { name, c ->
            when {
                name == "startPos" && c is ConstantControl && whichHalf == RIGHT ->
                    ConstantControl(reactiveVariable(c.value.now + position * (playBufRate?.now ?: 1.0)))

                else -> c.cut(position, whichHalf)
            }
        }
    )

    override fun getSpec(parameter: String): ControlSpec =
        if (parameter == "group") GroupControlSpec()
        else synthDef.getParameter(parameter).spec.now

    override fun initialize(context: Context) {
        if (initialized) return
        super.initialize(context)
        synthDefRef.resolve(context)
        controls.initialize(context, synthDef)
        controls.addView(this)
        parameterNameObserver = synthDef.parameters.observeEach { _, p ->
            p.name.observe { _, oldName, newName ->
                val control = controls.controlMap[oldName] ?: return@observe
                controls.removeControl(oldName)
                controls.addControl(newName, control)
            }
        }
    }

    private fun runOnActiveSynths(action: ScWriter.() -> Unit) {
        context[SuperColliderClient].run {
            for (synth in activeSynths) {
                appendBlock("if (~synths != nil && ~synths['$synth'] != nil && ~synths['$synth'].isRunning)") {
                    append("~synths['$synth'].")
                    action()
                }
                appendLine(";")
            }
        }
    }

    override fun addedControl(parameter: String, control: ParameterControl) {
        when (control) {
            is BusControl -> controlObservers[control] = control.bus.forEach { bus ->
                runOnActiveSynths { +"set('$parameter', ${bus.get().variableName})" }
            }

            is BusValueControl -> controlObservers[control] = control.bus.forEach { bus ->
                runOnActiveSynths { +"map('$parameter', ${bus.get().variableName})" }
            }

            is ConstantControl -> controlObservers[control] = control.value.forEach { value ->
                runOnActiveSynths { +"set('$parameter', $value)" }
            }

            is KnobControl -> controlObservers[control] = control.value.forEach { value ->
                runOnActiveSynths { +"set('$parameter', $value)" }
            }

            else -> {} //no realtime updates possible
        }
    }

    override fun removedControl(parameter: String, control: ParameterControl) {
        controlObservers.remove(control)?.kill()
    }

    override fun writeStartCode(writer: ScWriter, offset: Double, name: String) {
        activeSynths.add(name)
        writer.appendBlock("s.makeBundle(0)") {
            val constantArguments = controls.controlMap.mapNotNull { (param, control) ->
                when (control) {
                    is BufferControl -> param to (control.sample.now?.get()?.variableName ?: "0")
                    is BusControl -> param to control.bus.now.get().variableName
                    is ConstantControl -> {
                        val value = when (param) {
                            "startPos" -> control.value.now + offset
                            else -> control.value.now
                        }
                        param to value.toString()
                    }

                    is KnobControl -> param to control.get().toString()
                    else -> null
                }
            }.joinToString { (param, value) -> "$param: $value" }
            val synthVar = "~synths['$name']"
            val duration = "duration: ${duration - offset}"
            val group = group.now.get()
            +"$synthVar = Synth(\\${synthDef.name.now}, [$constantArguments, $duration], target: ${group.variableName})"
            +"$synthVar.register"
            for ((param, control) in controls.controlMap) {
                when (control) {
                    is EnvelopeControl -> {
                        val env = control.envelope.code(offset)
                        val busName = "~auxil_${name}_${param}"
                        +"$busName  = Bus.control(s, 1)"
                        +"{ $env }.play(s, $busName)"
                        +"${synthVar}.map(\\$param, $busName)"
                    }

                    is BusValueControl -> {
                        val bus = control.bus.now.get().variableName
                        +"${synthVar}.map(\\$param, $bus)"
                    }

                    is SingleBusValueControl -> {
                        val bus = control.bus.now.get().variableName
                        +"${synthVar}.set(\\$param, $bus.getSynchronized)"
                    }

                    is CustomControl -> {
                        val expr = control.expr.editor.result.now
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
        putSerializableValue("controls", controls)
    }

    object Serializer : ScoreObject.Serializer {
        override val type: String
            get() = "synth"

        override fun JsonObject.createFromJson(name: String): ScoreObject {
            val synthDef = getSerializableValue<InstrumentObject.Reference>("synthDef")!!
            @Suppress("UNCHECKED_CAST")
            synthDef as ObjectReference<SynthDefObject>
            val controls = getSerializableValue<SynthControls>("controls")!!
            return SynthObject(name, synthDef, controls)
        }
    }
}