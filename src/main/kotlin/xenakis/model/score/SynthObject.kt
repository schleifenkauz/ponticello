package xenakis.model.score

import hextant.context.Context
import javafx.geometry.HorizontalDirection
import javafx.geometry.HorizontalDirection.RIGHT
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.*
import xenakis.model.Settings
import xenakis.model.flow.ScoreObjectInfo
import xenakis.model.obj.*
import xenakis.model.player.ParameterControlLiveUpdater
import xenakis.model.player.PlaybackManager
import xenakis.model.registry.GroupRegistry
import xenakis.model.registry.reference
import xenakis.sc.ControlSpec
import xenakis.sc.client.SuperColliderClient
import xenakis.sc.editor.SynthDefSelector
import xenakis.sc.writeSynthCode
import xenakis.ui.impl.Direction

@Serializable
class SynthObject(
    @SerialName("name") override val mutableName: ReactiveVariable<String>,
    @SerialName("synthDef") private val synthDefRef: ReactiveVariable<SynthDefReference>,
    override val controls: ParameterControls
) : ScoreObject(), ParameterizedObject {
    override val type: String
        get() = "synth"

    @Transient
    lateinit var synthDefSelector: SynthDefSelector
        private set

    @Transient
    private var playBufRateBeforeResize = zero

    @Transient
    private lateinit var listener: ParameterControlLiveUpdater

    override val associatedControls: Map<String, ParameterControl>
        get() = controls.controlMap

    override fun getSpec(parameter: String): ControlSpec? = super<ParameterizedObject>.getSpec(parameter)

    val synthDef: SynthDefObject get() = synthDefRef.now.get() ?: NoSynthDef()

    override val def: ParameterizedObjectDef
        get() = synthDef

    val group: ReactiveValue<GroupReference> get() = (controls["group"] as GroupControl).group

    val groupObj get() = group.now.get() ?: context[GroupRegistry].getDefault()

    private val bufferControl get() = controls.controlMap["buf"] as? BufferControl

    val sample: ReactiveValue<SampleReference?> get() = bufferControl?.sample ?: reactiveVariable(null)

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
            val sampleDur = sample.now!!.get()?.duration ?: 0.0.asTime
            playbufStartPos!!.now = (playbufStartPos!!.now + playBufRate!!.now * duration).wrapAt(sampleDur)
            while (playbufStartPos!!.now < zero) playbufStartPos!!.now += sampleDur
            playBufRate!!.now *= -1
            if (playbufStartPos!!.now == zero && playBufRate!!.now < zero) playbufStartPos!!.now = sampleDur
        }
    }

    override fun initialize(context: Context) {
        if (initialized) return
        super.initialize(context)
        synthDefSelector = SynthDefSelector()
        synthDefSelector.syncWith(synthDefRef)
        synthDefSelector.initialize(context)
        controls.initialize(context, this)
        listener = ParameterControlLiveUpdater(context[SuperColliderClient], ::getActiveSynths)
        listener.listen(controls)
    }

    private fun getActiveSynths(): List<String> {
        if (!context.hasProperty(PlaybackManager) || !context[PlaybackManager].player.isPlaying.now) return emptyList()
        val activeInstances = context[PlaybackManager].graph.activeInstances(this@SynthObject)
        return activeInstances.map { i -> "~synths['${i.superColliderName.now}']" }
    }

    override fun writeCode(info: ScoreObjectInfo): String = code {
        appendBlock("s.makeBundle(${context[Settings].serverLatency.now})") {
            writeSynthCode(synthDef, context, info, duration, controls.controlMap)
        }
    }

    companion object {
        fun create(
            name: String, def: SynthDefObject,
            controls: ParameterControls = ParameterControls.empty()
        ): SynthObject {
            return SynthObject(reactiveVariable(name), reactiveVariable(def.reference()), controls)
        }
    }
}