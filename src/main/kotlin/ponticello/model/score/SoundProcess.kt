@file:OptIn(ExperimentalSerializationApi::class)

package ponticello.model.score

import fxutils.undo.UndoManager
import fxutils.undo.VariableEdit
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
import ponticello.model.flow.MidiTrackFlow
import ponticello.model.instr.*
import ponticello.model.obj.BufferReference
import ponticello.model.obj.InstrumentReference
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

    override val askBeforeDeleting: Boolean
        get() = getInstrument() !is MidiInstrument

    override fun soundProcessName(objectName: String): String = objectName

    override fun superColliderName(objectName: String): String = "SoundProcess.get('${soundProcessName}')"

    @Transient
    private var stretchControlBeforeResize = zero

    @Transient
    private var bufferPositionBeforeResize = zero

    override fun getInstrument(): InstrumentObject = instrumentRef.now.get() ?: NoInstrument()

    override fun validate(): Boolean = controls.validate()

    private val bufferControl get() = controls.controlMap["buf"] as? BufferControl

    val sample: ReactiveValue<BufferReference?> get() = bufferControl?.sample ?: reactiveVariable(null)

    val displaySample: ReactiveValue<Boolean>?
        get() = controls.getOrNull("buf")?.spec?.map { spec ->
            (spec as? BufferControlSpec)?.displaySpectrogram ?: false
        }

    val bufferOffset: ReactiveVariable<Decimal>?
        get() = getBufferPositionControl()?.first?.value

    val bufferStretchFactor: ReactiveVariable<Decimal>?
        get() = getStretchControl()?.first?.value

    override fun getSpec(parameter: String): ControlSpec? = super<ParameterizedObject>.getSpec(parameter)

    override fun activate() {
        super<ScoreObject>.activate()
        updater.startListening()
        controls.addListener(lfosManager)
    }

    override fun onRemoved() {
        updater.stopListening()
        controls.removeListener(lfosManager)
        super<ScoreObject>.onRemoved()
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
                    ValueControl(reactiveVariable(c.value.now + position * (bufferStretchFactor?.now ?: one(3))))
                } else c.copy()
            }

            c is EnvelopeControl && spec is NumericalControlSpec -> {
                val warp = spec.warp
                EnvelopeControl(c.points.cut(position, whichHalf, warp), c.displayColor, c.display)
            }

            else -> c.copy()
        }
    }

    private fun getNumericalControls() = controls.asSequence().mapNotNull { control ->
        val spec = control.spec.now as? NumericalControlSpec ?: return@mapNotNull null
        val ctrl = control.now as? ValueControl ?: return@mapNotNull null
        Triple(ctrl, spec, control.name.now)
    }

    fun getBufferPositionControl() = getNumericalControls()
        .singleOrNull { (_, spec, _) -> spec.origin is BufferPositionControlSpec }

    fun getStretchControl() = getNumericalControls()
        .singleOrNull { (_, spec, _) -> spec.isStretch }

    override fun beginResize(mode: ResizeMode, side: Side): Boolean {
        getBufferPositionControl()?.let { (ctrl, _, _) -> bufferPositionBeforeResize = ctrl.value.now }
        if (mode.isStretch) {
            getStretchControl()?.let { (ctrl, _, _) -> stretchControlBeforeResize = ctrl.value.now }
        }
        return super.beginResize(mode, side)
    }

    override fun resize(targetDuration: Decimal, targetHeight: Decimal) {
        if (resizeMode!!.isStretch) {
            getStretchControl()?.let { (ctrl, spec, _) ->
                ctrl.value.now = (stretchControlBeforeResize * (durationBeforeResize / targetDuration))
                    .roundToNearestMultiple(spec.step.get())
            }
        } else if (resizeSide == Side.LEFT) {
            getBufferPositionControl()?.let { (ctrl, spec, _) ->
                val rate = bufferStretchFactor?.now ?: one
                val deltaStart = durationBeforeResize - targetDuration
                ctrl.value.now = (bufferPositionBeforeResize + (deltaStart * rate))
                    .mod(spec.max.get())
                    .roundToNearestMultiple(spec.step.get())
                    .coerceAtMost(spec.max.get())
            }
        }
        super.resize(targetDuration, targetHeight)
    }

    override fun finishResize(recordEdit: Boolean) {
        super.finishResize(recordEdit)
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
        val (offset, offsetSpec) = getBufferPositionControl() ?: return@compoundEdit
        val stretchFactor = bufferStretchFactor ?: return@compoundEdit
        val sampleDur = offsetSpec.max.get()
        var startPos = (offset.value.now + stretchFactor.now * duration)
            .mod(sampleDur).roundToNearestMultiple(offsetSpec.step.get())
        VariableEdit.updateVariable(stretchFactor, -stretchFactor.now, context[UndoManager], "Update stretch factor")
        if (startPos == zero && stretchFactor.now < zero) startPos = sampleDur
        VariableEdit.updateVariable(offset.value, startPos, context[UndoManager], "Update buffer position")
    }

    override fun initialize(context: Context) {
        if (initialized) return
        super.initialize(context)
        instrumentRef.now.resolve(context[InstrumentRegistry])
        controls.initialize(this.context, this)
        updater = SoundProcessUpdater(this)
        lfosManager = LFOsManager()
    }

    override fun ScWriter.createInSuperCollider() {
        createSoundProcessObject(this@SoundProcess, duration)
    }

    override fun ScWriter.freeObject() {
        if (isCreatedInSuperCollider && getInstrument() !is MidiInstrument) {
            client.run("SoundProcess.remove('${name.now}')")
            isCreatedInSuperCollider = false
        }
    }

    override fun onRename(oldName: String, newName: String) {
        if (isCreatedInSuperCollider) {
            client.run("SoundProcess.rename('${soundProcessName(oldName)}', '${soundProcessName(newName)}')")
        }
    }

    override fun ScWriter.startNewInstance(info: ObjectPlaybackInfo) {
        val extraArgs = info.extraArguments.entries.mapTo(mutableListOf()) { (param, ctrl) ->
            ctrl.writeCode(param.name.now, param.spec.now, this@SoundProcess)
        }
        val parentObject = info.instance?.score?.parentObject
        var midiTrack: MidiTrackFlow? = null
        if (parentObject is MidiObject && getInstrument() is MidiInstrument) {
            val midinote = info.instance.y
            midiTrack = parentObject.track.now.get() ?: return
            extraArgs.add("ValueControl(\\pitch, $midinote)")
        }
        val latency = info.serverLatency
        append(
            superColliderName, ".startNewInstance(",
            info.pos, ",", info.cutoff, ",", extraArgs.joinToString(", ", "[", "]"), ",",
            latency, ",", info.player.id,
            ", midiTrack: ", midiTrack?.trackVariable ?: "nil", ")"
        )
    }

    companion object {
        fun create(
            name: String, instrument: InstrumentReference,
            controls: ParameterControlList = ParameterControlList.empty(),
        ): SoundProcess = SoundProcess(reactiveVariable(instrument), controls).withName(name)

        fun ScWriter.createSoundProcessObject(
            obj: ParameterizedObject, duration: Decimal? = null,
            className: String = "SoundProcess", extraArguments: List<String> = emptyList()
        ) = appendGroup("$className.create") {
            appendLine("name: '${obj.soundProcessName}',")
            appendLine("instr: ${obj.getInstrument().superColliderName},")
            append("controls: [")
            indented {
                for (ctrl in obj.controls) {
                    val parameter = ctrl.name.now
                    val expr = ctrl.now.writeCode(parameter, ctrl.spec.now, obj)
                    appendLine("$expr,")
                }
            }
            appendLine("],")
            if (duration != null) appendLine("duration: $duration,")
            for (arg in extraArguments) {
                append(arg)
                appendLine(",")
            }
        }
    }
}