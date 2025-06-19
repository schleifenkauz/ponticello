@file:OptIn(ExperimentalSerializationApi::class)

package ponticello.model.score

import hextant.context.Context
import javafx.geometry.HorizontalDirection
import javafx.geometry.HorizontalDirection.RIGHT
import javafx.geometry.Side
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonNames
import ponticello.impl.*
import ponticello.model.Settings
import ponticello.model.flow.NodePlacement
import ponticello.model.obj.*
import ponticello.model.player.ActiveObjectsManager
import ponticello.model.player.ActiveScoreObject
import ponticello.model.player.LiveSynthUpdater
import ponticello.model.player.ScorePlayer
import ponticello.model.registry.reference
import ponticello.model.score.controls.*
import ponticello.sc.BufferPositionControlSpec
import ponticello.sc.ControlSpec
import ponticello.sc.NumericalControlSpec
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.run
import ponticello.sc.editor.InstrumentSelector
import ponticello.ui.misc.LFOsManager
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
@SerialName("SoundProcess")
class SoundProcess(
    @JsonNames("synthDef", "processDef", "instrument") private val instrumentRef: ReactiveVariable<InstrumentReference>,
    override val controls: ParameterControlList,
): ScoreObject(), ParameterizedObject {
    @Transient
    private lateinit var controlListener: LiveSynthUpdater

    @Transient
    lateinit var lfosManager: LFOsManager
        private set

    override val associatedControls: Map<String, ParameterControl>
        get() = controls.controlMap

    override val type: String
        get() = "synth"

    override val superColliderPrefix: String get() = "~synth_"

    @Transient
    lateinit var instrumentSelector: InstrumentSelector
        private set

    @Transient
    private var playBufRateBeforeResize = zero

    val instrument: InstrumentObject get() = instrumentRef.now.get() ?: NoInstrument()

    override val def: InstrumentObject
        get() = instrument

    override fun validate(): Boolean = controls.validate()

    private val bufferControl get() = controls.controlMap["buf"] as? BufferControl

    val sample: ReactiveValue<BufferReference?> get() = bufferControl?.sample ?: reactiveVariable(null)

    val displaySample: ReactiveValue<Boolean>? get() = bufferControl?.display

    val playbufStartPos: ReactiveVariable<Decimal>?
        get() = (controls.controlMap["startPos"] as? ValueControl)?.value?.takeIf { bufferControl != null }

    val playBufRate: ReactiveVariable<Decimal>?
        get() = (controls.controlMap["rate"] as? ValueControl)?.value?.takeIf { bufferControl != null }

    override fun activeObjects(): List<ActiveScoreObject> = context[ActiveObjectsManager].activeInstances(this)

    override fun duration(): ReactiveValue<Decimal> = super<ScoreObject>.duration()

    override fun getSpec(parameter: String): ControlSpec? = super<ParameterizedObject>.getSpec(parameter)

    private fun initializeControls() {
        controls.initialize(context, this)
        controlListener = LiveSynthUpdater(this)
        lfosManager = LFOsManager()
    }

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

    override fun rename(newName: String) {
        ScorePlayer.execute {
            context[SuperColliderClient].run {
                activeObjects().forEach { active ->
                    val old = ActiveObjectsManager.uniqueName(name.now, active.suffix)
                    val new = ActiveObjectsManager.uniqueName(newName, active.suffix)
                    +"${ParameterControl.auxilBusesVar(new)} = ${ParameterControl.auxilBusesVar(old)}"
                    +"${ParameterControl.auxilBusesVar(old)} = nil"
                    +"${ParameterControl.auxilSynthsVar(new)} = ${ParameterControl.auxilSynthsVar(old)}"
                    +"${ParameterControl.auxilSynthsVar(old)} = nil"
                    appendLine()
                }
            }
        }
        super.rename(newName)
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
        var newDuration = targetDuration
        if (resizeMode.isStretch && playBufRate != null) {
            playBufRate!!.now *= (this.duration / newDuration)
        } else if (playbufStartPos != null) {
            if (resizeSide == Side.LEFT) {
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
        instrumentSelector = InstrumentSelector()
        instrumentSelector.syncWith(instrumentRef)
        instrumentSelector.initialize(context)
        initializeControls()
    }

    override fun writeCode(
        uniqueName: String, placement: NodePlacement?,
        cutoff: Decimal, latency: Decimal, extraArguments: Map<ParameterDefObject, ParameterControl>,
    ): String = writeCode {
        when (instrument) {
            is SynthDefObject -> {
                writeSynthCode(
                    this@SoundProcess, uniqueName, cutoff, placement!!,
                    latency, extraArguments, run = true
                )
            }

            is ProcessDefObject -> {
                writeProcessCode(
                    this@SoundProcess, uniqueName,
                    cutoff, context[Settings].serverLatency.get(),
                    extraArguments
                )
            }

            is NoInstrument -> Logger.error("$this has no instrument assigned")
        }
    }

    companion object {
        fun create(
            name: String, def: InstrumentObject,
            controls: ParameterControlList = ParameterControlList.empty(),
        ): SoundProcess = SoundProcess(reactiveVariable(def.reference()), controls).withName(name)
    }
}