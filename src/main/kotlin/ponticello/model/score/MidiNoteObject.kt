package ponticello.model.score

import hextant.context.Context
import javafx.scene.paint.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.*
import ponticello.model.flow.NodePlacement
import ponticello.model.obj.*
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.score.controls.*
import ponticello.sc.ControlSpec
import ponticello.sc.NumericalControlSpec
import ponticello.sc.code
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.now
import kotlin.math.pow

@Serializable
@SerialName("MidiNote")
@Deprecated("Use SoundProcess instead")
class MidiNoteObject(
    override val controls: ParameterControlList,
) : ScoreObject(), ParameterizedObject {
    @SerialName("name")
    override var _name: ReactiveVariable<String>? = null

    override val type: String
        get() = "midi-note"

    @Transient
    var _parentObject: MidiObject? = null

    var parentObject: MidiObject
        get() = _parentObject ?: error("Parent not initialized")
        private set(value) {
            _parentObject = value
        }

    override val associatedColor: ReactiveValue<Color?>
        get() = parentObject.associatedColor

    override val def: InstrumentObject
        get() = _parentObject?.def ?: NoInstrument()

    override val superColliderPrefix: String
        get() = "~midi_note_"

    override val canResizeVertically: Boolean
        get() = false

    override fun initialize(context: Context) {
        super.initialize(context)
        controls.initialize(context, this)
    }

    override fun duration(): ReactiveValue<Decimal> = super<ScoreObject>.duration()

    override fun getSpec(parameter: String): ControlSpec? = when (parameter) {
        "velocity" -> NumericalControlSpec.VELOCITY
        else -> super<ParameterizedObject>.getSpec(parameter)
    }

    override fun addedToScore(score: Score, group: AbstractScoreObjectGroup?) {
        check(group is MidiObject) { "Invalid parent for $this: $group" }
        this.parentObject = group
        super.addedToScore(score, group)
    }

    override fun writeCode(
        instance: ScoreObjectInstance?, uniqueName: String, placement: NodePlacement?,
        cutoff: Decimal, latency: Decimal, extraArguments: Map<ParameterDefObject, ParameterControl>,
    ): String {
        val parentObject = instance?.score?.parentObject as? MidiObject
        if (parentObject == null) {
            Logger.warn("$this is not the child of a MidiObject", Logger.Category.Playback)
            return ""
        }

        val midiLatency = parentObject.latencyMs.now
        val parentControls = parentObject.controls
        val combinedLatency = latency - (midiLatency / 1000.0.toDecimal())
        val midinote = instance.y
        val controlMap = parentControls.toMap() + controls.toMap() + extraArguments
        return when (val instr = parentObject.instrument.now) {
            InstrumentReference.None -> {
                Logger.warn("No instrument selected for $this", Logger.Category.Playback)
                ""
            }

            is InstrumentReference.UserDefined -> writeCode {
                val freq = (440 * 2.0.pow((midinote.value - 69) / 12)).toDecimal()
                val pitchControls = mapOf(
                    ParameterDefObject.MIDINOTE to ValueControl.create(midinote),
                    ParameterDefObject.FREQ to ValueControl.create(freq),
                )
                writeSynthCode(
                    this@MidiNoteObject, uniqueName, cutoff, placement!!, combinedLatency,
                    pitchControls + controlMap
                )
            }

            is InstrumentReference.VST -> {
                val controllerVar = instr.flow.get()?.controllerVar
                if (controllerVar == null) {
                     Logger.warn("VST instrument ${instr.flow} unresolved", Logger.Category.Playback)
                    return ""
                }
                val velocityCtrl =
                    extraArguments.entries.find { (k, _) -> k.name.now == "velocity" }?.value
                        ?: controls.getOrNull("velocity")?.now
                        ?: parentControls.getControl("velocity")

                val velocity = when (velocityCtrl) {
                    is BusValueControl -> "${velocityCtrl.bus.get().superColliderName}.getSynchronous"
                    is ExprControl -> velocityCtrl.expr.editor.result.now.code(context)
                    is ValueControl -> velocityCtrl.value.now.toString()
                    null -> "64"
                    else -> {
                        Logger.warn("Invalid velocity control: $velocityCtrl", Logger.Category.Playback)
                        "64"
                    }
                }
                writeCode {
                    +"TempoClock.sched($combinedLatency) { $controllerVar.midi.noteOn(0, $midinote, $velocity) }"
                    +"TempoClock.sched(${duration + combinedLatency}) { $controllerVar.midi.noteOff(0, $midinote) }"
                }
            }
        }
    }

    override fun doClone(): ScoreObject = MidiNoteObject(controls.copy())

    companion object {
        fun create(context: Context, duration: Decimal): MidiNoteObject {
            val obj = MidiNoteObject(ParameterControlList())
            obj.setInitialSize(duration, height = zero)
            val name = context[ScoreObjectRegistry].availableName("midinote")
            return obj.withName(name)
        }
    }
}