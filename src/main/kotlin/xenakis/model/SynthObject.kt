package xenakis.model

import hextant.context.Context
import javafx.geometry.HorizontalDirection
import javafx.geometry.HorizontalDirection.RIGHT
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.Observer
import reaktive.list.observeEach
import reaktive.value.*
import xenakis.impl.ScWriter
import xenakis.impl.SuperColliderClient
import xenakis.impl.code
import xenakis.impl.copy
import xenakis.sc.ControlSpec
import xenakis.sc.Identifier
import xenakis.sc.editor.SynthDefSelector
import xenakis.sc.transform
import xenakis.ui.Direction
import xenakis.ui.wrapAt

@Serializable
class SynthObject(
    @SerialName("name") override val mutableName: ReactiveVariable<String>,
    @SerialName("synthDef") private val synthDefRef: ReactiveVariable<ObjectReference>,
    val controls: SynthControls
) : ScoreObject(), SynthControls.View {
    @Transient
    private lateinit var parameterNameObserver: Observer
    override val type: String
        get() = "synth"

    @Transient
    private val controlObservers = mutableMapOf<ParameterControl, Observer>()

    @Transient
    lateinit var synthDefSelector: SynthDefSelector
        private set

    @Transient
    private var playBufRateBeforeResize = 0.0

    val synthDef: SynthDefObject get() = synthDefRef.now.get()

    val parameters
        get() = controls.extraParameters + synthDef.parameters.now
            .filter { param -> controls.getExtraSpec(param.name.now) == null }

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
        reactiveVariable(newName), synthDefRef.copy(),
        controls = controls.copy()
    )

    override fun doCut(position: Double, whichHalf: HorizontalDirection, newName: String): ScoreObject = SynthObject(
        reactiveVariable(newName), synthDefRef.copy(),
        controls = controls.transformControls { name, c ->
            when {
                name == "startPos" && c is ConstantControl && whichHalf == RIGHT ->
                    ConstantControl(reactiveVariable(c.value.now + position * (playBufRate?.now ?: 1.0)))

                else -> c.cut(position, whichHalf)
            }
        }
    )

    override fun beginResize(stretch: Boolean, direction: Direction) {
        super.beginResize(stretch, direction)
        if (stretch && playBufRate != null) {
            playBufRateBeforeResize = playBufRate!!.now
        }
    }

    override fun resize(targetDuration: Double, targetHeight: Double) {
        var newDuration = targetDuration
        if (stretchResize && playBufRate != null) {
            playBufRate!!.now *= (this.duration / newDuration)
        } else if (playbufStartPos != null) {
            if (resizeDirection.left) {
                val rate = playBufRate?.now ?: 1.0
                newDuration = newDuration.coerceAtMost(this.duration + playbufStartPos!!.now)
                val deltaStart = this.duration - newDuration
                playbufStartPos!!.now += deltaStart * rate
            }
        }
        super.resize(newDuration, targetHeight)
    }

    override fun finishResize() {
        super.finishResize()
        if (stretchResize && playBufRate != null) {
            playBufRate!!.now = playBufRateBeforeResize * (durationBeforeResize / duration)
        }
    }

    fun reverse() {
        for (ctrl in controls.controlMap.values) {
            if (ctrl is EnvelopeControl) {
                ctrl.envelope.reverse()
            }
        }
        if (sample.now != null && playBufRate != null && playbufStartPos != null) {
            val sampleDur = sample.now!!.get<SampleObject>().duration
            playbufStartPos!!.now = (playbufStartPos!!.now + playBufRate!!.now * duration).wrapAt(sampleDur)
            while (playbufStartPos!!.now < 0.0) playbufStartPos!!.now += sampleDur
            playBufRate!!.now *= -1
            if (playbufStartPos!!.now == 0.0 && playBufRate!!.now < 0.0) playbufStartPos!!.now = sampleDur
        }
    }

    override fun getSpec(parameter: String): ControlSpec? =
        controls.getExtraSpec(parameter) ?: synthDef.getParameter(parameter)?.spec?.now

    override fun initialize(context: Context) {
        if (initialized) return
        super.initialize(context)
        synthDefSelector = SynthDefSelector(context, synthDefRef)
        parameterNameObserver = synthDef.parameters.observeEach { _, p ->
            p.name.observe { _, oldName, newName ->
                val control = controls.controlMap[oldName] ?: return@observe
                val extraSpec = controls.getExtraSpec(oldName)
                controls.removeControl(oldName)
                controls.addControl(newName, control, extraSpec = extraSpec)
            }
        }
        controls.initialize(context, synthDef)
        controls.addView(this)
    }

    private fun runOnActiveSynths(action: ScWriter.() -> Unit) {
        if (!context.hasProperty(PlaybackManager) || !context[PlaybackManager].player.isPlaying) return
        context[SuperColliderClient].run {
            for ((_, _, name) in context[PlaybackManager].env.activeInstances(this@SynthObject)) {
                appendBlock("if (~synths != nil && ~synths['$name'] != nil && ~synths['$name'].isRunning)") {
                    append("~synths['$name'].")
                    action()
                }
            }
        }
    }

    override fun addedControl(parameter: String, control: ParameterControl) {
        when (control) {
            is BusControl -> controlObservers[control] = control.bus.forEach { bus ->
                runOnActiveSynths { +"set('$parameter', ${bus.get<BusObject>().superColliderName})" }
            }

            is BusValueControl -> controlObservers[control] = control.bus.forEach { bus ->
                runOnActiveSynths { +"map('$parameter', ${bus.get<BusObject>().superColliderName})" }
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

    override fun writeCode(name: String, position: ObjectPosition, env: ScorePlayEnv): String = code {
        appendBlock("s.makeBundle(${env.serverLatency})") {
            val constantArguments = controls.controlMap.mapNotNull { (param, control) ->
                if (!synthDef.hasParameter(param)) null
                else when (control) {
                    is BufferControl -> param to (control.sample.now?.get<SampleObject>()?.superColliderName ?: "0")
                    is BusControl -> param to control.bus.now.get<BusObject>().superColliderName
                    is ConstantControl -> {
                        val value = when (param) {
                            "startPos" -> control.value.now * (playBufRate?.now ?: 0.0)
                            else -> control.value.now
                        }
                        param to value.toString()
                    }

                    is KnobControl -> param to control.get().toString()
                    is EnvelopeControl -> param to control.envelope.points[0].y
                    else -> null
                }
            }.joinToString { (param, value) -> "$param: $value" } + ", duration: $duration"
            val synthVar = "~synths['$name']"
            val group = group.now
            val synthDefName = synthDef.name.now
            val (addAction, target) = env.getSynthOrderFor(group, position)
            +"$synthVar = Synth(\\$synthDefName, [$constantArguments], target: $target, addAction: $addAction)"
            +"$synthVar.register"
            for ((param, control) in controls.controlMap) {
                when (control) {
                    is EnvelopeControl -> {
                        val envelopeCode = control.envelope.code()
                        val busName = "~auxil_${name}_${param}"
                        +"$busName  = Bus.control(s, 1)"
                        +"{ $envelopeCode }.play(s, $busName)"
                        +"${synthVar}.map(\\$param, $busName)"
                    }

                    is BusValueControl -> {
                        val bus = control.bus.now.get<BusObject>().superColliderName
                        +"${synthVar}.map(\\$param, $bus)"
                    }

                    is SingleBusValueControl -> {
                        val bus = control.bus.now.get<BusObject>().superColliderName
                        +"${synthVar}.set(\\$param, $bus.getSynchronized)"
                    }

                    is CustomControl -> {
                        val code = controls.controlMap.entries
                            .associate { (name, control) -> "~ctrl_$name" to control.makeExpr() }
                        val expr = control.expr.editor.result.now
                            .transform<Identifier> { e -> code[e.text] ?: e }
                        val busName = "~auxil_${name}_${param}"
                        +"$busName = Bus.control(s, 1)"
                        appendBlock("", endLine = false) {
                            +"Env.new(levels: [0, 0], times: [$duration]).kr(Done.freeSelf)"
                            expr.code(writer, context)
                        }
                        +".play(s, $busName)"
                        +"${synthVar}.map(\\$param, $busName)"
                    }

                    else -> {} //already handled in constantArguments
                }
            }
        }
    }
}