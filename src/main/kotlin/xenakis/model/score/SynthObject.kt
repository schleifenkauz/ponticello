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
import xenakis.model.flow.NodePlacement
import xenakis.model.obj.*
import xenakis.model.player.LiveSynthUpdater
import xenakis.model.registry.GroupRegistry
import xenakis.model.registry.reference
import xenakis.model.score.controls.*
import xenakis.sc.ControlSpec
import xenakis.sc.NumericalControlSpec
import xenakis.sc.editor.SynthDefSelector
import xenakis.ui.impl.Direction

@Serializable
class SynthObject(
    @SerialName("name") override val mutableName: ReactiveVariable<String>,
    @SerialName("synthDef") private val synthDefRef: ReactiveVariable<SynthDefReference>,
    override val controls: ParameterControlList,
) : ScoreObject(), ParameterizedObject {
    override val type: String
        get() = "synth"

    override val superColliderPrefix: String get() = "~synth_"

    @Transient
    lateinit var synthDefSelector: SynthDefSelector
        private set

    @Transient
    private var playBufRateBeforeResize = zero

    @Transient
    private lateinit var controlListener: LiveSynthUpdater

    val synthDef: SynthDefObject get() = synthDefRef.now.get() ?: NoSynthDef()

    override val def: ParameterizedObjectDef
        get() = synthDef

    override val associatedControls: Map<String, ParameterControl>
        get() = controls.controlMap

    override fun getSpec(parameter: String): ControlSpec? = super<ParameterizedObject>.getSpec(parameter)

    override fun validate(): Boolean = controls.validate()

    val group: ReactiveVariable<GroupReference>? get() = (controls.controlMap["group"] as GroupControl?)?.group

    val groupObj get() = group?.now?.get() ?: context[GroupRegistry].getDefault()

    private val bufferControl get() = controls.controlMap["buf"] as? BufferControl

    val sample: ReactiveValue<BufferReference?> get() = bufferControl?.sample ?: reactiveVariable(null)

    val displaySample: ReactiveValue<Boolean>? get() = bufferControl?.display

    val playbufStartPos: ReactiveVariable<Decimal>?
        get() = (controls.controlMap["startPos"] as? ValueControl)?.value?.takeIf { bufferControl != null }

    val playBufRate: ReactiveVariable<Decimal>?
        get() = (controls.controlMap["rate"] as? ValueControl)?.value?.takeIf { bufferControl != null }

    override fun doClone(newName: String): ScoreObject = SynthObject(
        reactiveVariable(newName), synthDefRef.copy(),
        controls = controls.copy()
    )

    override fun doCut(position: Decimal, whichHalf: HorizontalDirection, newName: String): ScoreObject = SynthObject(
        reactiveVariable(newName), synthDefRef.copy(),
        controls = cutEnvelopes(whichHalf, position)
    )

    private fun cutEnvelopes(
        whichHalf: HorizontalDirection,
        position: Decimal,
    ) = controls.transformControls { ctrl ->
        val c = ctrl.now
        val name = ctrl.name.now
        when {
            name == "startPos" && c is ValueControl && whichHalf == RIGHT ->
                ValueControl(reactiveVariable(c.value.now + position * (playBufRate?.now ?: one(3))))

            c is EnvelopeControl -> {
                val spec = ctrl.spec.now as? NumericalControlSpec ?: return@transformControls c
                val warp = spec.warp
                EnvelopeControl(c.points.cut(position, whichHalf, warp), c.displayColor, c.display)
            }

            else -> c
        }
    }

    override fun beginResize(mode: ResizeMode, direction: Direction): Boolean {
        if (mode.isStretch && playBufRate != null) {
            playBufRateBeforeResize = playBufRate!!.now
        }
        return super.beginResize(mode, direction)
    }

    override fun resize(targetDuration: Decimal, targetHeight: Decimal) {
        var newDuration = targetDuration
        if (resizeMode.isStretch && playBufRate != null) {
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
        if (resizeMode.isStretch && playBufRate != null) {
            playBufRate!!.now = playBufRateBeforeResize * (durationBeforeResize / duration)
        }
    }

    fun reverse() {
        for (ctrl in controls.controlMap.values) {
            if (ctrl is EnvelopeControl) {
                ctrl.points.reverse()
            }
        }
        if (sample.now != null && playBufRate != null && playbufStartPos != null) {
            val sampleDur = sample.now!!.get()?.duration()?.now ?: 0.0.asTime
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
        controlListener = LiveSynthUpdater(this)
    }

    override fun onLoadedIntoRegistry() {
        super<ScoreObject>.onLoadedIntoRegistry()
        controlListener.startListening()
    }

    override fun onRemoved() {
        super<ScoreObject>.onRemoved()
        controlListener.stopListening()
    }

    override fun writeCode(uniqueName: String, placement: NodePlacement?, cutoff: Decimal): String = writeCode {
        writeSynthCode(this@SynthObject, uniqueName, cutoff, placement!!, context[Settings].serverLatency.now)
    }

    companion object {
        fun create(
            name: String, def: SynthDefObject,
            controls: ParameterControlList = ParameterControlList.empty(),
        ): SynthObject {
            return SynthObject(reactiveVariable(name), reactiveVariable(def.reference()), controls)
        }
    }
}