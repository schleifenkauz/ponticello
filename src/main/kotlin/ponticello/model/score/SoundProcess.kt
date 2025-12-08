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
import ponticello.model.player.ScorePlayer
import ponticello.model.player.SoundProcessUpdater
import ponticello.model.score.controls.*
import ponticello.sc.*
import ponticello.sc.client.ScWriter
import ponticello.ui.misc.LFOsManager
import reaktive.value.*
import reaktive.value.binding.flatMap
import reaktive.value.binding.map
import reaktive.value.binding.orElse

@Serializable
@SerialName("SoundProcess")
class SoundProcess(
    @JsonNames("synthDef", "processDef", "instrument") val instrumentRef: ReactiveVariable<InstrumentReference>,
    override val controls: ParameterControlList,
) : ScoreObject(), ParameterizedObject {
    @SerialName("name")
    override var _name: ReactiveVariable<String>? = null

    @Transient
    private lateinit var controlListener: SoundProcessUpdater<SoundProcess>

    @Transient
    lateinit var lfosManager: LFOsManager
        private set

    override val associatedControls: Map<String, ParameterControl>
        get() = controls.controlMap

    override val associatedColor: ReactiveValue<Color?>
        get() = super.associatedColor.orElse(instrumentRef.flatMap { ref ->
            ref.get()?.color ?: reactiveValue(null)
        })

    override val type: String
        get() = "synth"

    override val superColliderPrefix: String
        get() = when (def) {
            is SynthDefObject -> "~synth_"
            is ProcessDefObject -> "~proc_"
            else -> "~unknown_"
        }

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
        controlListener.startListening()
        controls.addListener(lfosManager)
    }

    override fun onRemoved() {
        super<ScoreObject>.onRemoved()
        controlListener.stopListening()
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
        controlListener = SoundProcessUpdater(this)
        lfosManager = LFOsManager()
    }

    override fun ScWriter.createObject() {
        createSoundProcessObject(writer, this@SoundProcess, duration)
    }

    override fun onRename(oldName: String, newName: String) {
        client.run("SoundProcess.rename('${soundProcessName(oldName)}', '${soundProcessName(newName)}')")
    }

    override fun startNewInstance(
        pos: ObjectPosition, cutoff: Decimal, instance: ScoreObjectInstance?,
        latency: Decimal, player: ScorePlayer, extraArguments: Map<ParameterDefObject, ParameterControl>
    ): String {
        val midiNote = "nil"
        return "$superColliderName.startNewInstance($pos, $cutoff, $midiNote, $latency, ${player.id})"
    }

    //    override fun writeCode(): String = writeCode {
//        var controlMap = controls.toMap() + extraArguments
//        var latency = serverLatency
//        val midiObject = instance?.score?.parentObject as? MidiObject
//        if (midiObject != null) {
//            val midinote = instance.y
//            val freq = (440 * 2.0.pow((midinote.value - 69) / 12)).toDecimal()
//            val pitchControls = mapOf(
//                ParameterDefObject.MIDINOTE to ValueControl.create(midinote),
//                ParameterDefObject.FREQ to ValueControl.create(freq),
//            )
//            controlMap = midiObject.controls.toMap() + pitchControls + controlMap
//            latency -= (midiObject.latencyMs.now / 1000.toDecimal())
//        }
//        when (val instr = def) {
//            is SynthDefObject -> {
//                writeSynthCode(
//                    this@SoundProcess, uniqueName, cutoff, placement!!,
//                    latency, controlMap + extraArguments, run = true
//                )
//            }
//
//            is ProcessDefObject -> {
//                writeProcessCode(
//                    this@SoundProcess, uniqueName,
//                    cutoff, controlMap + extraArguments
//                )
//            }
//
//            is VSTInstrumentObject -> {
//                val controllerVar = instr.flow.controllerVar
//                val velocityCtrl = extraArguments.entries.find { (k, _) -> k.name.now == "velocity" }?.value
//                    ?: controls.getOrNull("velocity")?.now
//                    ?: midiObject?.controls?.getControl("velocity")
//
//                val velocity = velocityCtrl.controlToExprString() ?: "64"
//                val midinoteCtrl = extraArguments.entries.find { (k, _) -> k.name.now == "midinote" }?.value
//                    ?: controls.getOrNull("midinote")?.now
//                    ?: midiObject?.controls?.getControl("midinote")
//                val midinote = midinoteCtrl?.controlToExprString() ?: instance?.y?.toString() ?: "60"
//                +"TempoClock.sched($latency) { $controllerVar.midi.noteOn(0, $midinote, $velocity) }"
//                +"TempoClock.sched(${duration + latency}) { $controllerVar.midi.noteOff(0, $midinote) }"
//            }
//
//            is NoInstrument -> Logger.error("$this has no instrument assigned")
//        }
//    }

    companion object {
        fun create(
            name: String, instrument: InstrumentReference,
            controls: ParameterControlList = ParameterControlList.empty(),
        ): SoundProcess = SoundProcess(reactiveVariable(instrument), controls).withName(name)

        private fun ParameterControl?.controlToExprString(): String? =
            when (this) {
                is BusValueControl -> "${bus.get().superColliderName}.getSynchronous"
                is ExprControl -> expr.editor.result.now.code(context)
                is ValueControl -> value.now.toString()
                null -> null
                else -> {
                    Logger.warn("Invalid velocity control: $this", Logger.Category.Playback)
                    null
                }
            }

        fun createSoundProcessObject(writer: ScWriter, obj: ParameterizedObject, duration: Decimal?) =
            writer.appendGroup("SoundProcess.create") {
                appendLine("name: '${obj.soundProcessName}',")
                appendLine("def: ${obj.def.superColliderName},")
                appendLine("duration: ${duration ?: "nil"},")
                append("controls: [")
                indented {
                    for (ctrl in obj.controls) {
                        val parameter = ctrl.name.now
                        if (parameter == "attack-release") continue //TODO
                        val expr = ctrl.now.writeCode(parameter, ctrl.spec.now, obj)
                        appendLine("$expr,")
                    }
                }
                appendLine("]")
            }
    }
}