package ponticello.model.score

import fxutils.Direction
import hextant.context.Context
import javafx.geometry.HorizontalDirection
import javafx.geometry.HorizontalDirection.RIGHT
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.*
import ponticello.model.flow.NodePlacement
import ponticello.model.obj.*
import ponticello.model.registry.reference
import ponticello.model.score.controls.*
import ponticello.sc.BufferPositionControlSpec
import ponticello.sc.NumericalControlSpec
import ponticello.sc.editor.SynthDefSelector
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
class SynthObject(
    @SerialName("synthDef") private val synthDefRef: ReactiveVariable<SynthDefReference>,
    override val controls: ParameterControlList,
) : ParameterizedScoreObject() {
    override val type: String
        get() = "synth"

    override val superColliderPrefix: String get() = "~synth_"

    @Transient
    lateinit var synthDefSelector: SynthDefSelector
        private set

    @Transient
    private var playBufRateBeforeResize = zero

    val synthDef: SynthDefObject get() = synthDefRef.now.get() ?: NoSynthDef()

    override val def: ParameterizedObjectDef
        get() = synthDef

    override fun validate(): Boolean = controls.validate()

    private val bufferControl get() = controls.controlMap["buf"] as? BufferControl

    val sample: ReactiveValue<BufferReference?> get() = bufferControl?.sample ?: reactiveVariable(null)

    val displaySample: ReactiveValue<Boolean>? get() = bufferControl?.display

    val playbufStartPos: ReactiveVariable<Decimal>?
        get() = (controls.controlMap["startPos"] as? ValueControl)?.value?.takeIf { bufferControl != null }

    val playBufRate: ReactiveVariable<Decimal>?
        get() = (controls.controlMap["rate"] as? ValueControl)?.value?.takeIf { bufferControl != null }

    override fun doClone(): ScoreObject = SynthObject(
        synthDefRef.copy(), controls = controls.copy()
    )

    override fun doCut(position: Decimal, whichHalf: HorizontalDirection): ScoreObject = SynthObject(
        synthDefRef.copy(), controls = cutEnvelopes(whichHalf, position)
    )

    private fun cutEnvelopes(
        whichHalf: HorizontalDirection,
        position: Decimal,
    ) = controls.transformControls { ctrl ->
        val c = ctrl.now
        when {
            c is ValueControl && ctrl.spec.now is BufferPositionControlSpec && whichHalf == RIGHT ->
                ValueControl(reactiveVariable(c.value.now + position * (playBufRate?.now ?: one(3))))

            c is EnvelopeControl -> {
                val spec = ctrl.spec.now as? NumericalControlSpec ?: return@transformControls c
                val warp = spec.warp
                EnvelopeControl(c.points.cut(position, whichHalf, warp), c.displayColor, c.display)
            }

            else -> c.copy()
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
            var startPos = (playbufStartPos!!.now + playBufRate!!.now * duration).wrapAt(sampleDur)
            while (startPos < zero) startPos += sampleDur
            playBufRate!!.now *= -1
            if (startPos == zero && playBufRate!!.now < zero) startPos = sampleDur
            playbufStartPos!!.now = startPos
        }
    }

    override fun initialize(context: Context) {
        if (initialized) return
        super.initialize(context)
        synthDefSelector = SynthDefSelector()
        synthDefSelector.syncWith(synthDefRef)
        synthDefSelector.initialize(context)
        initializeControls()
    }

    override fun writeCode(
        uniqueName: String, placement: NodePlacement?,
        cutoff: Decimal, latency: Decimal, extraArguments: Map<ParameterDefObject, ParameterControl>,
    ): String = writeCode {
        writeSynthCode(this@SynthObject, uniqueName, cutoff, placement!!, latency, extraArguments, run = true)
    }

    companion object {
        fun create(
            name: String, def: SynthDefObject,
            controls: ParameterControlList = ParameterControlList.empty(),
        ): SynthObject = SynthObject(reactiveVariable(def.reference()), controls).withName(name)
    }
}