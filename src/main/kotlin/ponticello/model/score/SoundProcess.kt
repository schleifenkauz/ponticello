@file:OptIn(ExperimentalSerializationApi::class)

package ponticello.model.score

import hextant.context.Context
import hextant.context.compoundEdit
import javafx.geometry.HorizontalDirection
import javafx.geometry.HorizontalDirection.RIGHT
import javafx.geometry.Side
import javafx.scene.paint.Color
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonNames
import ponticello.impl.*
import ponticello.model.instr.*
import ponticello.model.obj.BufferReference
import ponticello.model.obj.withName
import ponticello.model.player.ObjectPlaybackInfo
import ponticello.model.player.SoundProcessUpdater
import ponticello.model.score.controls.*
import ponticello.sc.BufferControlSpec
import ponticello.sc.BufferPositionControlSpec
import ponticello.sc.ControlSpec
import ponticello.sc.NumericalControlSpec
import ponticello.sc.client.ScWriter
import ponticello.ui.misc.LFOsManager
import reaktive.Reactive
import reaktive.value.*
import reaktive.value.binding.flatMap
import reaktive.value.binding.map
import reaktive.value.binding.orElse
import kotlin.math.pow

@Serializable
@SerialName("SoundProcess")
class SoundProcess(
    @JsonNames("synthDef", "processDef", "instrument") val instrumentRef: ReactiveVariable<InstrumentReference>,
    override val controls: ParameterControlList,
) : ScoreObject(), ParameterizedObject {
    @SerialName("name")
    override var _name: ReactiveVariable<String>? = null

    @Transient
    private lateinit var updater: SoundProcessUpdater<SoundProcess>

    @Transient
    lateinit var lfosManager: LFOsManager
        private set

    override val associatedControls: Map<String, ParameterControl>
        get() = controls.controlMap

    override val instrumentChanged: Reactive
        get() = instrumentRef

    override val associatedColor: ReactiveValue<Color?>
        get() = super.associatedColor.orElse(instrumentRef.flatMap { ref ->
            ref.get()?.color ?: reactiveValue(null)
        })

    override val type: String
        get() = "synth"

    override fun soundProcessName(objectName: String): String = "proc_${objectName}"

    override fun superColliderName(objectName: String): String = "SoundProcess.get('${soundProcessName}')"

    @Transient
    private var playBufRateBeforeResize = zero

    override val def: InstrumentObject
        get() = instrumentRef.now.get() ?: NoInstrument()

    override fun validate(): Boolean = controls.validate()

    private val bufferControl get() = controls.controlMap["buf"] as? BufferControl

    val sample: ReactiveValue<BufferReference?> get() = bufferControl?.sample ?: reactiveVariable(null)

    val displaySample: ReactiveValue<Boolean>?
        get() = controls.getOrNull("buf")?.spec?.map { spec ->
            (spec as? BufferControlSpec)?.displaySpectrogram ?: false
        }

    val playbufStartPos: ReactiveVariable<Decimal>?
        get() = (controls.controlMap["startPos"] as? ValueControl)?.value?.takeIf { bufferControl != null }

    val playBufRate: ReactiveVariable<Decimal>?
        get() = (controls.controlMap["rate"] as? ValueControl)?.value?.takeIf { bufferControl != null }

    override fun duration(): ReactiveValue<Decimal> = super<ScoreObject>.duration()

    override fun getSpec(parameter: String): ControlSpec? = super<ParameterizedObject>.getSpec(parameter)

    override fun onLoadedIntoRegistry() {
        super<ScoreObject>.onLoadedIntoRegistry()
        updater.startListening()
        controls.addListener(lfosManager)
    }

    override fun onRemoved() {
        super<ScoreObject>.onRemoved()
        updater.stopListening()
        controls.removeListener(lfosManager)
    }

    override fun doClone(): ScoreObject = SoundProcess(
        instrumentRef.copy(), controls = controls.copy()
    )

    override fun doCut(position: Decimal, whichHalf: HorizontalDirection): ScoreObject = SoundProcess(
        instrumentRef.copy(), controls = cutEnvelopes(whichHalf, position)
    )

    private fun cutEnvelopes(
        whichHalf: HorizontalDirection,
        position: Decimal,
    ) = controls.transformControls { ctrl ->
        val c = ctrl.now
        val spec = ctrl.spec.now
        when {
            c is ValueControl && spec is NumericalControlSpec && whichHalf == RIGHT -> {
                if (spec.origin is BufferPositionControlSpec) {
                    ValueControl(reactiveVariable(c.value.now + position * (playBufRate?.now ?: one(3))))
                } else c.copy()
            }

            c is EnvelopeControl && spec is NumericalControlSpec -> {
                val warp = spec.warp
                EnvelopeControl(c.points.cut(position, whichHalf, warp), c.displayColor, c.display)
            }

            else -> c.copy()
        }
    }

    override fun beginResize(mode: ResizeMode, side: Side): Boolean {
        if (mode.isStretch && playBufRate != null) {
            playBufRateBeforeResize = playBufRate!!.now
        }
        return super.beginResize(mode, side)
    }

    override fun resize(targetDuration: Decimal, targetHeight: Decimal) {
        val ratePrecision = (getSpec("rate") as? NumericalControlSpec)?.precision ?: 3
        if (resizeMode!!.isStretch && playBufRate != null) {
            playBufRate!!.now = (playBufRate!!.now * (this.duration / targetDuration)).round(ratePrecision)
        } else if (playbufStartPos != null) {
            if (resizeSide == Side.LEFT) {
                val rate = playBufRate?.now ?: one(ratePrecision)
                val deltaStart = this.duration - targetDuration
                var startPos = playbufStartPos!!.now
                startPos += deltaStart * rate
                val buf = sample.now?.get()
                if (buf != null) {
                    val dur = buf.duration().now
                    startPos = ((startPos % dur) + dur) % dur
                }
                playbufStartPos!!.now = startPos.round(ratePrecision)
            }
        }
        super.resize(targetDuration, targetHeight)
    }

    override fun finishResize(recordEdit: Boolean) {
        super.finishResize(recordEdit)
        if (resizeMode!!.isStretch && playBufRate != null) {
            playBufRate!!.now = playBufRateBeforeResize * (durationBeforeResize / duration)
        }
        if (isCreatedInSuperCollider) {
            client.run("$superColliderName.duration = $duration")
        }
    }

    fun reverse(reverseEnvelopes: Boolean) = context.compoundEdit("Reverse SoundProcess") {
        if (reverseEnvelopes) {
            for (ctrl in controls.controlMap.values) {
                if (ctrl is EnvelopeControl) {
                    ctrl.points.reverse()
                }
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
        instrumentRef.now.resolve(context)
        controls.initialize(this.context, this)
        updater = SoundProcessUpdater(this)
        lfosManager = LFOsManager()
    }

    override fun ScWriter.createInSuperCollider() {
        createSoundProcessObject(writer, this@SoundProcess, duration)
    }

    override fun onRename(oldName: String, newName: String) {
        if (isCreatedInSuperCollider) {
            client.run("SoundProcess.rename('${soundProcessName(oldName)}', '${soundProcessName(newName)}')")
        }
    }

    override fun ScWriter.startNewInstance(info: ObjectPlaybackInfo) {
        val extraArgs = info.extraArguments.entries.associateTo(mutableMapOf()) { (param, ctrl) ->
            val valueCtrl = ctrl as? ValueControl ?: error("Expected ValueControl for $param")
            param.name.now to valueCtrl.value.now
        }
        var latency = info.serverLatency
        val midiObject = info.instance?.score?.parentObject as? MidiObject
        if (midiObject != null) {
            val midinote = info.instance.y
            extraArgs["midinote"] = midinote
            if (def is SynthDefObject || def is ProcessDefObject) {
                val freq = (440 * 2.0.pow((midinote.value - 69) / 12)).toDecimal()
                extraArgs["freq"] = freq
            }
            latency -= (midiObject.latencyMs.now / 1000.toDecimal())
        }
        val extraArgsString = extraArgs.entries.joinToString(", ", "(", ")") { (name, value) -> "$name: $value" }
        append(
            superColliderName, ".startNewInstance(",
            info.pos, ",", info.cutoff, ",", extraArgsString, ",",
            latency, ",", info.player.id, ")"
        )
    }

    companion object {
        fun create(
            name: String, instrument: InstrumentReference,
            controls: ParameterControlList = ParameterControlList.empty(),
        ): SoundProcess = SoundProcess(reactiveVariable(instrument), controls).withName(name)

        fun createSoundProcessObject(writer: ScWriter, obj: ParameterizedObject, duration: Decimal?) =
            writer.appendGroup("SoundProcess.create") {
                appendLine("name: '${obj.soundProcessName}',")
                appendLine("instr: ${obj.def.superColliderName},")
                appendLine("duration: ${duration ?: "nil"},")
                append("controls: [")
                indented {
                    for (ctrl in obj.controls) {
                        val parameter = ctrl.name.now
                        val expr = ctrl.now.writeCode(parameter, ctrl.spec.now, obj)
                        appendLine("$expr,")
                    }
                }
                appendLine("]")
            }
    }
}