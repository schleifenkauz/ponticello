package ponticello.model.score

import fxutils.undo.AbstractEdit
import hextant.context.Context
import hextant.context.withoutUndo
import javafx.geometry.Side
import javafx.scene.paint.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.*
import ponticello.model.instr.InstrumentObject
import ponticello.model.instr.MidiInstrument
import ponticello.model.instr.ParameterizedObject
import ponticello.model.midi.MidiEvent
import ponticello.model.obj.MidiTrackReference
import ponticello.model.obj.project
import ponticello.model.project.flows
import ponticello.model.score.controls.ParameterControlList
import ponticello.sc.ControlSpec
import reaktive.Reactive
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveValue
import reaktive.value.reactiveVariable

@Serializable
@SerialName("MidiObject")
class MidiObject(
    val track: ReactiveVariable<MidiTrackReference>,
    @SerialName("lowestPitch") private var _lowestPitch: Int,
    @SerialName("highestPitch") private var _highestPitch: Int,
    override val score: Score,
    override val controls: ParameterControlList = ParameterControlList(),
    val extraMessages: MutableList<MidiEvent> = mutableListOf(),
    override val associatedColor: ReactiveVariable<
            @Serializable(with = ColorSerializer::class) Color
            > = reactiveVariable(Color.WHITE)
) : AbstractScoreObjectGroup(), ParameterizedObject {
    @SerialName("name")
    override var _name: ReactiveVariable<String>? = null

    override val type: String
        get() = "midi"

    var lowestPitch
        get() = _lowestPitch
        set(value) {
            _lowestPitch = value
            notifyListeners<Listener> { updatedPitchRange() }
        }

    var highestPitch
        get() = _highestPitch
        set(value) {
            _highestPitch = value
            notifyListeners<Listener> { updatedPitchRange() }
        }

    override val minY: Decimal
        get() = lowestPitch.toDecimal()
    override val maxY: Decimal
        get() = highestPitch.toDecimal()

    val pitchRange get() = lowestPitch..highestPitch

    @Transient
    private var pixelsPerPitch: Double = -1.0

    private val notes get() = score.objectInstances

    override fun getInstrument(): InstrumentObject = MidiInstrument

    override val instrumentChanged: Reactive
        get() = reactiveValue(false)

    override fun initialize(context: Context) {
        super.initialize(context)
        track.now.resolve(context.project.flows.allMidiTracks())
        controls.initialize(context, this)
    }

    override fun beginResize(mode: ResizeMode, side: Side): Boolean {
        pixelsPerPitch = (height / (highestPitch - lowestPitch + 1)).value
        return super.beginResize(mode, side)
    }

    override fun computeMinHeight(objects: List<ScoreObjectInstance>, resizeSide: Side): Decimal {
        return when {
            objects.isEmpty() -> zero(ObjectPosition.Y_PRECISION)
            resizeSide == Side.BOTTOM -> this.height - objects.minOf { n -> pixelsPerPitch * (n.y - lowestPitch) }
            resizeSide == Side.TOP -> objects.maxOf { n ->
                (pixelsPerPitch * (n.y - lowestPitch)) + pixelsPerPitch
            }

            else -> zero(ObjectPosition.Y_PRECISION)
        }
    }

    override fun resize(targetDuration: Decimal, targetHeight: Decimal) {
        if (targetDuration == this.duration && targetHeight == this.height) return
        when {
            resizeMode!!.isStretch -> super.resize(targetDuration, targetHeight)
            resizeSide!!.isVertical -> super.resize(targetDuration, targetHeight)
            else -> {
                val minHeight = computeMinHeight(notes, resizeSide!!)
                val deltaHeight = targetHeight.coerceAtLeast(minHeight) - this.height
                val pitches = ((this.height + deltaHeight) / pixelsPerPitch).ceilToInt()
                if (pitches != pitchRange.count()) {
                    if (resizeSide == Side.TOP) highestPitch = lowestPitch + pitches
                    else if (resizeSide == Side.BOTTOM) lowestPitch = highestPitch - pitches
                }
                super.resize(this.duration, (pitches * pixelsPerPitch).withPrecision(ObjectPosition.Y_PRECISION))
            }
        }
    }

    fun transpose(deltaPitch: Int) {
        context.withoutUndo {
            lowestPitch += deltaPitch
            highestPitch += deltaPitch
            for (note in notes) {
                note.moveTo(note.start, note.y + deltaPitch, simpleMove = true)
            }
        }
        recordEdit(TransposeEdit(this, deltaPitch))
    }

    override fun cloneWith(score: Score): MidiObject =
        MidiObject(
            track.copy(), lowestPitch, highestPitch,
            score, controls.copy(), extraMessages, associatedColor.copy()
        )

    override fun getSpec(parameter: String): ControlSpec? = super<ParameterizedObject>.getSpec(parameter)

    class TransposeEdit(private val obj: MidiObject, private val deltaPitch: Int) : AbstractEdit() {
        override val actionDescription: String
            get() = "Transpose"

        override fun doUndo() {
            obj.transpose(-deltaPitch)
        }

        override fun doRedo() {
            obj.transpose(deltaPitch)
        }
    }

    interface Listener : ScoreObject.Listener {
        fun updatedPitchRange()
    }
}