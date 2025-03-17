package xenakis.model.score

import hextant.context.Context
import hextant.context.withoutUndo
import hextant.core.editor.initialized
import hextant.serial.EditorRoot
import hextant.undo.AbstractEdit
import hextant.undo.PropertyEdit
import hextant.undo.UndoManager
import javafx.geometry.HorizontalDirection
import javafx.geometry.HorizontalDirection.LEFT
import javafx.geometry.HorizontalDirection.RIGHT
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.*
import xenakis.model.flow.ScoreObjectInfo
import xenakis.model.obj.*
import xenakis.model.registry.ObjectReference
import xenakis.sc.code
import xenakis.sc.editor.*
import xenakis.ui.impl.Direction
import xenakis.ui.score.PianoRollObjectView

@Serializable
class PianoRollObject(
    @SerialName("name") override val mutableName: ReactiveVariable<String>,
    @SerialName("instrument") private val mInstrument: ReactiveVariable<InstrumentReference>,
    @SerialName("lowestPitch") private var mLowestPitch: Int,
    @SerialName("highestPitch") private var mHighestPitch: Int,
    val eventDictionary: EditorRoot<@Contextual EventDictionaryEditor>,
    private val notes: MutableList<Note>
) : ScoreObject() {
    override val type: String
        get() = "piano-roll"

    @Transient
    lateinit var instrumentSelector: InstrumentSelector
        private set

    private val instrument get() = mInstrument.now.get() ?: NoSynthDef()

    var lowestPitch
        get() = mLowestPitch
        set(value) {
            mLowestPitch = value
            notifyListeners<PianoRollObjectView> { updatedPitchRange() }
        }

    var highestPitch
        get() = mHighestPitch
        set(value) {
            mHighestPitch = value
            notifyListeners<PianoRollObjectView> { updatedPitchRange() }
        }

    val pitchRange get() = lowestPitch..highestPitch

    @Transient
    private var pixelsPerPitch: Double = -1.0

    override fun initialize(context: Context) {
        if (initialized) return
        super.initialize(context)
        instrumentSelector = InstrumentSelector()
        instrumentSelector.syncWith(mInstrument)
        instrumentSelector.initialize(context)
        for (note in notes) {
            note.parent = this
        }
    }

    override fun beginResize(type: ResizeType, direction: Direction): Boolean {
        pixelsPerPitch = (height / (highestPitch - lowestPitch + 1)).value
        return super.beginResize(type, direction)
    }

    override fun resize(targetDuration: Decimal, targetHeight: Decimal) {
        if (resizeType.isStretch) {
            val horizontalRatio = targetDuration / this.duration
            super.resize(targetDuration, targetHeight)
            for (note in notes) {
                note.onset *= horizontalRatio
                note.duration *= horizontalRatio
            }
        } else {
            var minDur = zero(ObjectPosition.TIME_PRECISION)
            var minHeight = zero(ObjectPosition.Y_PRECISION)
            if (notes.isNotEmpty()) {
                minDur = when {
                    resizeDirection.left -> this.duration - notes.minOf { n -> n.onset }
                    resizeDirection.right -> notes.maxOf { o -> o.onset + o.duration }
                    else -> minDur
                }

                minHeight = when {
                    resizeDirection.down -> this.height - notes.minOf { n -> pixelsPerPitch * (n.midinote - lowestPitch) }
                    resizeDirection.up -> notes.maxOf { n ->
                        (pixelsPerPitch * (n.midinote - lowestPitch)) + pixelsPerPitch
                    }.asY

                    else -> minHeight
                }
            }
            val deltaDur = targetDuration.coerceAtLeast(minDur) - this.duration
            val deltaHeight = targetHeight.coerceAtLeast(minHeight) - this.height
            val pitches = ((this.height + deltaHeight) / pixelsPerPitch).ceilToInt()
            if (pitches != pitchRange.count()) {
                if (resizeDirection.up) highestPitch = lowestPitch + pitches
                else if (resizeDirection.down) lowestPitch = highestPitch - pitches
            }
            super.resize(this.duration + deltaDur, (pitches * pixelsPerPitch).withPrecision(ObjectPosition.Y_PRECISION))
            if (resizeDirection.left) {
                for (note in this.notes) {
                    note.onset += deltaDur
                }
            }
        }
    }

    fun addTime(position: Decimal, amount: Decimal) {
        require(position in zero..duration) { "Invalid position $position not in 0..$duration" }
        recordEdit(Edit.AddTime(this, position, amount))
        context.withoutUndo {
            for (note in notes) {
                if (note.onset >= position) note.onset += amount
            }
            duration += amount
        }
    }

    fun deleteTimeRange(from: Decimal, to: Decimal) {
        require(zero <= from && from < to && to < duration) { "Invalid time range: $from..$to" }
        recordEdit(Edit.DeleteTimeRange(this, from, to))
        context.withoutUndo {
            duration -= to - from
            for (note in notes) {
                if (note.onset >= from) {
                    if (note.onset + note.duration <= to) {
                        removeNote(note)
                    } else if (note.onset >= to) {
                        note.onset -= (to - from)
                    } else {
                        note.onset = from
                    }
                }
            }
        }
    }

    fun addNote(note: Note) {
        notes.add(note)
        note.parent = this
        context[UndoManager].record(Edit.AddNote(this, note))
        notifyListeners<PianoRollObjectView> { addedNote(note) }
    }

    fun removeNote(note: Note) {
        notes.remove(note)
        context[UndoManager].record(Edit.RemoveNote(this, note))
        notifyListeners<PianoRollObjectView> { removedNote(note) }
    }

    fun transpose(deltaPitch: Int) {
        context.withoutUndo {
            lowestPitch += deltaPitch
            highestPitch += deltaPitch
            for (note in notes) {
                note.midinote += deltaPitch
            }
        }
        recordEdit(Edit.Transpose(this, deltaPitch))
    }

    private fun updateNote(note: Note) {
        notifyListeners<PianoRollObjectView> { updatedNote(note) }
    }

    override fun addListener(view: Listener) {
        super.addListener(view)
        if (view is PianoRollObjectView) {
            for (note in notes) {
                view.addedNote(note)
            }
        }
    }

    override fun doClone(newName: String): ScoreObject = PianoRollObject(
        reactiveVariable(newName), mInstrument.copy(), lowestPitch, highestPitch,
        context.withoutUndo { eventDictionary.clone(context) },
        notes.mapTo(mutableListOf()) { n -> n.copy() }
    )

    override fun doCut(position: Decimal, whichHalf: HorizontalDirection, newName: String): ScoreObject {
        val notes = when (whichHalf) {
            LEFT -> notes.filter { n -> n.onset < position }
            RIGHT -> notes.filter { n -> n.onset >= position }
        }.mapTo(mutableListOf()) { n -> n.copy() }
        return PianoRollObject(
            reactiveVariable(newName), mInstrument,
            lowestPitch, highestPitch,
            eventDictionary.clone(context), notes
        )
    }

    override fun writeCode(
        info: ScoreObjectInfo
    ): String = code {
        val generalEventDict = eventDictionary.editor.result.now
        for ((idx, n) in notes.withIndex()) {
            val t = n.onset
            if (t < -n.duration) continue
            val dur = n.duration + t.coerceAtMost(zero)
            val midinote = n.midinote
            val eventDict = n.eventDictionary.editor.result.now
            val eventMap = mutableMapOf<String, String>()
            eventMap["duration"] = dur.toString()
            for ((key, value) in eventDict.entries) eventMap[key.text] = value.code(context)
            for ((key, value) in generalEventDict.entries) eventMap[key.text] = value.code(context)
            val playNote = when (val instr = instrument) {
                is SynthDefObject -> {
                    eventMap["freq"] = "$midinote.midicps + ${eventMap["detune"] ?: 0}.midiratio"
                    eventMap.remove("detune")
                    val namedValues = eventMap.entries.joinToString { (name, value) -> "$name: $value" }
                    val synthName = "~synths['${name}_${idx}']"
                    "$synthName = Synth(\\${instr.name.now}, [${namedValues}])"
                }

                is VSTPluginObject -> {
                    eventMap["midinote"] = midinote.toString()
                    eventMap["type"] = "\\vst_midi"
                    eventMap["vst"] = instr.superColliderName
                    val namedValues = eventMap.entries.joinToString { (name, value) -> "$name: $value" }
                    "(type: \\vst_midi, vst: ${instr.superColliderName}, $namedValues).play"
                }

                else -> error("Unknown instrument type $instr")
            }
            appendBlock("TempoClock.sched(${t.coerceAtLeast(zero)})") {
                +playNote
            }
        }
    }

    abstract class Edit(protected val obj: PianoRollObject, protected val note: Note) : AbstractEdit() {
        class AddNote(obj: PianoRollObject, note: Note) : Edit(obj, note) {
            override val actionDescription: String
                get() = "Add note"

            override fun doUndo() {
                obj.removeNote(note)
            }

            override fun doRedo() {
                obj.addNote(note)
            }
        }

        class RemoveNote(obj: PianoRollObject, note: Note) : Edit(obj, note) {
            override val actionDescription: String
                get() = "Remove note"

            override fun doUndo() {
                obj.addNote(note)
            }

            override fun doRedo() {
                obj.removeNote(note)
            }
        }

        class Transpose(private val obj: PianoRollObject, private val deltaPitch: Int) : AbstractEdit() {
            override val actionDescription: String
                get() = "Transpose"

            override fun doUndo() {
                obj.transpose(-deltaPitch)
            }

            override fun doRedo() {
                obj.transpose(deltaPitch)
            }
        }

        class AddTime(
            private val obj: PianoRollObject, private val position: Decimal, private val amount: Decimal
        ) : AbstractEdit() {
            override val actionDescription: String
                get() = "Add time"

            override fun doRedo() {
                obj.addTime(position, amount)
            }

            override fun doUndo() {
                obj.deleteTimeRange(position, position + amount)
            }
        }

        class DeleteTimeRange(
            private val obj: PianoRollObject, private val from: Decimal, private val to: Decimal
        ) : AbstractEdit() {
            override val actionDescription: String
                get() = "Remove time range"

            override fun doRedo() {
                obj.deleteTimeRange(from, to)
            }

            override fun doUndo() {
                obj.addTime(from, amount = to - from)
            }
        }

    }

    @Serializable
    class Note(
        private var _time: Decimal,
        private var _duration: Decimal,
        private var _midinote: Int,
        val eventDictionary: EditorRoot<@Contextual EventDictionaryEditor>
    ) {
        @Transient
        lateinit var parent: PianoRollObject

        var onset: Decimal by this::_time.reactive { oldValue, newValue ->
            parent.context[UndoManager].record(PropertyEdit(this::onset, oldValue, newValue, "Edit note time"))
            parent.updateNote(this)
        }
        var duration: Decimal by this::_duration.reactive { oldValue, newValue ->
            parent.context[UndoManager].record(PropertyEdit(this::duration, oldValue, newValue, "Edit note time"))
            parent.updateNote(this)
        }
        var midinote: Int by this::_midinote.reactive { oldValue, newValue ->
            parent.context[UndoManager].record(PropertyEdit(this::midinote, oldValue, newValue, "Edit note time"))
            parent.updateNote(this)
        }

        override fun toString(): String = "Note(start: $onset, dur: $duration, pitch: $midinote)"

        fun copy() = Note(onset, duration, midinote, eventDictionary.clone())

        companion object {
            fun create(context: Context, time: Decimal, duration: Decimal, midinote: Int): Note {
                val eventDictionary = EventDictionaryEditor()
                context.withoutUndo {
                    eventDictionary.entries.addLast(
                        NamedExprEditor(
                            IdentifierEditor("velocity"),
                            ScExprExpander("60")
                        )
                    )
                }
                return Note(time, duration, midinote, EditorRoot.create(eventDictionary, context))
            }
        }
    }
}