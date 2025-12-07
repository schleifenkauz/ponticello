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
import ponticello.model.instr.*
import ponticello.model.score.controls.ParameterControlList
import ponticello.sc.ControlSpec
import reaktive.value.*
import reaktive.value.binding.flatMap
import reaktive.value.binding.orElse

@Serializable
@SerialName("MidiObject")
class MidiObject(
    val instrument: ReactiveVariable<InstrumentReference>,
    @SerialName("lowestPitch") private var _lowestPitch: Int,
    @SerialName("highestPitch") private var _highestPitch: Int,
    override val score: Score,
    override val controls: ParameterControlList,
    val latencyMs: ReactiveVariable<Int> = reactiveVariable(0),
) : AbstractScoreObjectGroup(), ParameterizedObject {
    @SerialName("name")
    override var _name: ReactiveVariable<String>? = null

    override val type: String
        get() = "midi"

    override val superColliderPrefix: String
        get() = "~midi_"

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

    override val associatedColor: ReactiveValue<Color?>
        get() = super.associatedColor.orElse(instrument.flatMap { instr ->
            if (instr is InstrumentReference.UserDefined) instr.reference.get()?.color ?: reactiveValue(null)
            else reactiveValue(null)
        })

    override val def: InstrumentObject
        get() = when (val instr = instrument.now) {
            is InstrumentReference.UserDefined -> instr.reference.get() ?: NoInstrument()
            is InstrumentReference.VST -> instr.flow.get()?.let { f -> VSTInstrumentObject(f) } ?: NoInstrument()
            InstrumentReference.None -> NoInstrument()
        }

    @Transient
    private var pixelsPerPitch: Double = -1.0

    private val notes get() = score.objectInstances

    override fun initialize(context: Context) {
        super.initialize(context)
        instrument.now.resolve(context)
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
        MidiObject(instrument.copy(), lowestPitch, highestPitch, score, controls.copy(), latencyMs.copy())

    override fun doClone(): ScoreObject = cloneWith(score.deepClone())

    override fun duration(): ReactiveValue<Decimal> = super<AbstractScoreObjectGroup>.duration()

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