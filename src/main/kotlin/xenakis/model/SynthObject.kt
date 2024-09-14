package xenakis.model

import hextant.context.Context
import hextant.core.editor.ListenerManager
import javafx.geometry.HorizontalDirection
import javafx.geometry.HorizontalDirection.RIGHT
import javafx.geometry.VerticalDirection
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.Observer
import reaktive.collection.observeCollection
import reaktive.list.observeEach
import reaktive.value.*
import xenakis.impl.ScWriter
import xenakis.impl.code
import xenakis.sc.ControlSpec
import xenakis.sc.GroupControlSpec
import xenakis.ui.ScorePlayer
import xenakis.ui.SynthObjectView

@Serializable
class SynthObject(
    override val mutableName: ReactiveVariable<String>,
    private val synthDefRef: ObjectReference,
    val controls: SynthControls
) : ScoreObject(), SynthControls.View {
    @Transient
    private lateinit var parameterNameObserver: Observer
    override val type: String
        get() = "synth"

    @Transient
    override val viewManager: ListenerManager<SynthObjectView> = ListenerManager.createWeakListenerManager()

    @Transient
    private val controlObservers = mutableMapOf<ParameterControl, Observer>()

    @Transient
    private lateinit var parameterObserver: Observer

    @Transient
    private val myActiveSynths = mutableSetOf<String>()

    val synthDef: SynthDefObject get() = synthDefRef.get()

    val group: ReactiveValue<ObjectReference> get() = (controls["group"] as GroupControl).group

    private val bufferControl get() = controls.controlMap["buf"] as? BufferControl

    val sample: ReactiveValue<ObjectReference?> get() = bufferControl?.sample ?: reactiveVariable(null)

    val displaySample: ReactiveValue<Boolean>? get() = bufferControl?.display

    val playbufStartPos: ReactiveVariable<Double>?
        get() = (controls.controlMap["startPos"] as? ConstantControl)?.value?.takeIf { bufferControl != null }

    val playBufRate: ReactiveVariable<Double>?
        get() = (controls.controlMap["rate"] as? ConstantControl)?.value?.takeIf { bufferControl != null }

    override val associatedControls: Map<String, ParameterControl> get() = controls.controlMap

    override fun doClone(newName: String): ScoreObject = SynthObject(
        reactiveVariable(newName), synthDefRef,
        controls = controls.copy()
    )

    override fun doCut(position: Double, whichHalf: HorizontalDirection, newName: String): ScoreObject = SynthObject(
        reactiveVariable(newName), synthDefRef,
        controls = controls.transformControls { name, c ->
            when {
                name == "startPos" && c is ConstantControl && whichHalf == RIGHT ->
                    ConstantControl(reactiveVariable(c.value.now + position * (playBufRate?.now ?: 1.0)))

                else -> c.cut(position, whichHalf)
            }
        }
    )

    override fun resize(
        duration: Double,
        height: Double,
        stretch: Boolean,
        horizontalDirection: HorizontalDirection?,
        verticalDirection: VerticalDirection?
    ) {
        var newDuration = duration
        if (stretch && playBufRate != null) {
            playBufRate!!.now *= (duration / newDuration)
        } else if (playbufStartPos != null) {
            if (horizontalDirection == HorizontalDirection.LEFT) {
                val rate = playBufRate?.now ?: 1.0
                newDuration = newDuration.coerceAtMost(duration + playbufStartPos!!.now)
                val deltaStart = duration - newDuration
                playbufStartPos!!.now += deltaStart * rate
            }
        }
        super.resize(newDuration, height, stretch, horizontalDirection, verticalDirection)
    }

    fun reverse() {
        for (ctrl in controls.controlMap.values) {
            if (ctrl is EnvelopeControl) {
                ctrl.envelope.reverse()
            }
        }
        if (sample.now != null && playBufRate != null && playbufStartPos != null) {
            playbufStartPos!!.now += (playBufRate!!.now * duration).coerceAtMost(sample.now!!.get<SampleObject>().duration)
            playBufRate!!.now *= -1
        }
    }

    override fun getSpec(parameter: String): ControlSpec =
        if (parameter == "group") GroupControlSpec()
        else synthDef.getParameter(parameter).spec.now

    override fun initialize(context: Context) {
        if (initialized) return
        super.initialize(context)
        synthDefRef.resolve(context[InstrumentRegistry])
        parameterObserver = synthDef.parameters.observeCollection(
            added = { _, param -> controls.addControl(param.name.now, param.defaultControl(context)) },
            removed = { _, param -> controls.removeControl(param.name.now) }
        )
        parameterNameObserver = synthDef.parameters.observeEach { _, p ->
            p.name.observe { _, oldName, newName ->
                val control = controls.controlMap[oldName] ?: return@observe
                controls.removeControl(oldName)
                controls.addControl(newName, control)
            }
        }
        controls.initialize(context, synthDef)
        controls.addView(this)
    }

    private fun runOnActiveSynths(action: ScWriter.() -> Unit) {
        /*
                context[SuperColliderClient].run {
                    for (synth in myActiveSynths) {
                        appendBlock("if (~synths != nil && ~synths['$synth'] != nil && ~synths['$synth'].isRunning)") {
                            append("~synths['$synth'].")
                            action()
                        }
                        appendLine(";")
                    }
                }
        */
    }

    override fun addedControl(parameter: String, control: ParameterControl) {
        when (control) {
            is BusControl -> controlObservers[control] = control.bus.forEach { bus ->
                runOnActiveSynths { +"set('$parameter', ${bus.get<BusObject>().variableName})" }
            }

            is BusValueControl -> controlObservers[control] = control.bus.forEach { bus ->
                runOnActiveSynths { +"map('$parameter', ${bus.get<BusObject>().variableName})" }
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

    override fun writeCode(env: ScorePlayEnv, name: String, cutoff: Double): String = code {
        myActiveSynths.add(name)
        appendBlock("s.makeBundle(${ScorePlayer.SERVER_LATENCY})") {
            val constantArguments = controls.controlMap.mapNotNull { (param, control) ->
                when (control) {
                    is BufferControl -> param to (control.sample.now?.get<SampleObject>()?.variableName ?: "0")
                    is BusControl -> param to control.bus.now.get<BusObject>().variableName
                    is ConstantControl -> {
                        val value = when (param) {
                            "startPos" -> control.value.now + cutoff * (playBufRate?.now ?: 0.0)
                            else -> control.value.now
                        }
                        param to value.toString()
                    }

                    is KnobControl -> param to control.get().toString()
                    else -> null
                }
            }.joinToString { (param, value) -> "$param: $value" } + ", duration: ${duration - cutoff}"
            val synthVar = "~synths['$name']"
            val group = group.now
            val synthDefName = synthDef.name.now
            val parallelSynths = env.activeSynths(group)
            //TODO we need the position here
            val runBefore = parallelSynths
                //.filter { (_, _, pos) -> pos.y > this@SynthObject }
                .minByOrNull { (_, _, pos) -> pos.y }
            val runAfter = parallelSynths
                //.filter { (_, _, pos) -> pos.y < y }
                .maxByOrNull { (_, _, pos) -> pos.y }
            val (addAction, target) = when {
                runAfter != null -> Pair("'addAfter'", "~synths['${runAfter.name}']")
                runBefore != null -> Pair("'addBefore'", "~synths['${runBefore.name}']")
                else -> Pair("'addToHead'", group.get<GroupObject>().variableName)
            }
            +"$synthVar = Synth(\\$synthDefName, [$constantArguments], target: $target, addAction: $addAction)"
            +"$synthVar.register"
            for ((param, control) in controls.controlMap) {
                when (control) {
                    is EnvelopeControl -> {
                        val envelopeCode = control.envelope.code(cutoff)
                        val busName = "~auxil_${name}_${param}"
                        +"$busName  = Bus.control(s, 1)"
                        +"{ $envelopeCode }.play(s, $busName)"
                        +"${synthVar}.map(\\$param, $busName)"
                    }

                    is BusValueControl -> {
                        val bus = control.bus.now.get<BusObject>().variableName
                        +"${synthVar}.map(\\$param, $bus)"
                    }

                    is SingleBusValueControl -> {
                        val bus = control.bus.now.get<BusObject>().variableName
                        +"${synthVar}.set(\\$param, $bus.getSynchronized)"
                    }

                    is CustomControl -> {
                        val expr = control.expr.editor.result.now
                        val busName = "~auxil_${name}_${param}"
                        +"$busName = Bus.control(s, 1)"
                        append("{ ")
                        expr.code(writer, context)
                        +" }.play(s, $busName)"
                        +"${synthVar}.map(\\$param, $busName)"
                    }

                    else -> {} //already handled in constantArguments
                }
            }
            //+"$synthVar.run"
        }
    }
}