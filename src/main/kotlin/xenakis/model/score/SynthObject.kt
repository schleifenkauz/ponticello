package xenakis.model.score

import hextant.context.Context
import javafx.geometry.HorizontalDirection
import javafx.geometry.HorizontalDirection.RIGHT
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.Observer
import reaktive.value.*
import xenakis.impl.*
import xenakis.model.obj.BusObject
import xenakis.model.obj.ParameterizedObjectDef
import xenakis.model.obj.SampleObject
import xenakis.model.obj.SynthDefObject
import xenakis.model.player.PlaybackManager
import xenakis.model.player.ScorePlayEnv
import xenakis.model.registry.ObjectReference
import xenakis.sc.Identifier
import xenakis.sc.client.ScWriter
import xenakis.sc.client.SuperColliderClient
import xenakis.sc.editor.SynthDefSelector
import xenakis.sc.transform
import xenakis.ui.impl.Direction

@Serializable
class SynthObject(
    @SerialName("name") override val mutableName: ReactiveVariable<String>,
    @SerialName("synthDef") private val synthDefRef: ReactiveVariable<ObjectReference>,
    override val controls: ParameterControls
) : ParameterizedScoreObject(), ParameterControls.View {
    override val type: String
        get() = "synth"

    @Transient
    private val controlObservers = mutableMapOf<ParameterControl, Observer>()

    @Transient
    lateinit var synthDefSelector: SynthDefSelector
        private set

    @Transient
    private var playBufRateBeforeResize = zero

    val synthDef: SynthDefObject get() = synthDefRef.now.get()

    override val def: ParameterizedObjectDef
        get() = synthDef

    val group: ReactiveValue<ObjectReference> get() = (controls["group"] as GroupControl).group

    private val bufferControl get() = controls.controlMap["buf"] as? BufferControl

    val sample: ReactiveValue<ObjectReference?> get() = bufferControl?.sample ?: reactiveVariable(null)

    val displaySample: ReactiveValue<Boolean>? get() = bufferControl?.display

    val playbufStartPos: ReactiveVariable<Decimal>?
        get() = (controls.controlMap["startPos"] as? ConstantControl)?.value?.takeIf { bufferControl != null }

    val playBufRate: ReactiveVariable<Decimal>?
        get() = (controls.controlMap["rate"] as? ConstantControl)?.value?.takeIf { bufferControl != null }

    override fun doClone(newName: String): ScoreObject = SynthObject(
        reactiveVariable(newName), synthDefRef.copy(),
        controls = controls.copy()
    )

    override fun doCut(position: Decimal, whichHalf: HorizontalDirection, newName: String): ScoreObject = SynthObject(
        reactiveVariable(newName), synthDefRef.copy(),
        controls = controls.transformControls { name, c ->
            when {
                name == "startPos" && c is ConstantControl && whichHalf == RIGHT ->
                    ConstantControl(reactiveVariable(c.value.now + position * (playBufRate?.now ?: one(3))))

                else -> c.cut(position, whichHalf)
            }
        }
    )

    override fun beginResize(type: ResizeType, direction: Direction): Boolean {
        if (type.isStretch && playBufRate != null) {
            playBufRateBeforeResize = playBufRate!!.now
        }
        return super.beginResize(type, direction)
    }

    override fun resize(targetDuration: Decimal, targetHeight: Decimal) {
        var newDuration = targetDuration
        if (resizeType.isStretch && playBufRate != null) {
            playBufRate!!.now *= (this.duration / newDuration)
        } else if (playbufStartPos != null) {
            if (resizeDirection.left) {
                val rate = playBufRate?.now ?: one(precision = 3)
                newDuration = newDuration.coerceAtMost(this.duration + playbufStartPos!!.now)
                val deltaStart = this.duration - newDuration
                playbufStartPos!!.now += deltaStart * rate
            }
        }
        super.resize(newDuration, targetHeight)
    }

    override fun finishResize(recordEdit: Boolean) {
        super.finishResize(recordEdit)
        if (resizeType.isStretch && playBufRate != null) {
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
            while (playbufStartPos!!.now < zero) playbufStartPos!!.now += sampleDur
            playBufRate!!.now *= -1
            if (playbufStartPos!!.now == zero && playBufRate!!.now < zero) playbufStartPos!!.now = sampleDur
        }
    }

    override fun initialize(context: Context) {
        if (initialized) return
        super.initialize(context)
        synthDefSelector = SynthDefSelector(context, synthDefRef)
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
                    is ConstantControl -> param to control.value.now.toString()
                    is KnobControl -> param to control.get().toString()
                    is EnvelopeControl -> param to control.envelope.points[0].value.toString()
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