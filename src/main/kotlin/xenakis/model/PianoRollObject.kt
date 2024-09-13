package xenakis.model

import hextant.context.Context
import hextant.context.withoutUndo
import hextant.core.editor.ListenerManager
import hextant.serial.EditorRoot
import hextant.undo.AbstractEdit
import hextant.undo.PropertyEdit
import hextant.undo.UndoManager
import javafx.geometry.HorizontalDirection
import javafx.geometry.HorizontalDirection.LEFT
import javafx.geometry.HorizontalDirection.RIGHT
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.code
import xenakis.impl.reactive
import xenakis.sc.code
import xenakis.sc.editor.*
import xenakis.ui.PianoRollObjectView
import xenakis.ui.ScoreObjectView
import kotlin.math.roundToInt

@Serializable
class PianoRollObject(
    override val mutableName: ReactiveVariable<String>,
    private val initialInstrument: ObjectReference,
    @SerialName("lowestPitch") private var _lowestPitch: Int,
    @SerialName("highestPitch") private var _highestPitch: Int,
    val eventDictionary: EditorRoot<EventDictionaryEditor>,
    private val notes: MutableList<Note>
) : ScoreObject() {
    override val type: String
        get() = "piano-roll"

    @Transient
    override val viewManager: ListenerManager<PianoRollObjectView> = ListenerManager.createWeakListenerManager()

    @Transient
    lateinit var instrumentSelector: InstrumentSelector
        private set

    val instrument get() = if (initialized) instrumentSelector.result.now else initialInstrument

    var lowestPitch
        get() = _lowestPitch
        set(value) {
            _lowestPitch = value
            viewManager.notifyListeners { updatedPitchRange() }
        }

    var highestPitch
        get() = _highestPitch
        set(value) {
            _highestPitch = value
            viewManager.notifyListeners { updatedPitchRange() }
        }

    val pitchRange get() = lowestPitch..highestPitch

    val pixelsPerPitch get() = height / (highestPitch - lowestPitch)

    fun notes(): List<Note> = notes

    override fun initialize(context: Context) {
        if (initialized) return
        super.initialize(context)
        instrumentSelector = InstrumentSelector(context, reactiveVariable(initialInstrument))
        for (note in notes) {
            note.parent = this
        }
    }

    fun addTime(position: Double, amount: Double) {
        require(position in 0.0..duration) { "Invalid position $position not in 0..$duration" }
        recordEdit(Edit.AddTime(this, position, amount))
        context.withoutUndo {
            for (note in notes) {
                if (note.onset >= position) note.onset += amount
            }
            duration += amount
        }
    }

    fun deleteTimeRange(from: Double, to: Double) {
        require(0.0 <= from && from < to && to < duration) { "Invalid time range: $from..$to" }
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
        viewManager.notifyListeners { addedNote(note) }
    }

    fun removeNote(note: Note) {
        notes.remove(note)
        context[UndoManager].record(Edit.RemoveNote(this, note))
        viewManager.notifyListeners { removedNote(note) }
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

    fun getY(pitch: Int) = (highestPitch - pitch) * pixelsPerPitch - pixelsPerPitch

    fun getMidiNote(y: Double): Int = ((height - y) / pixelsPerPitch).roundToInt() + lowestPitch - 1

    private fun updateNote(note: Note) {
        viewManager.notifyListeners { updatedNote(note) }
    }

    override fun addView(view: ScoreObjectView) {
        super.addView(view)
        if (view is PianoRollObjectView) {
            for (note in notes) {
                view.addedNote(note)
            }
        }
    }

    override fun doClone(newName: String): ScoreObject = PianoRollObject(
        reactiveVariable(newName), instrument, lowestPitch, highestPitch,
        context.withoutUndo { eventDictionary.clone() },
        notes.mapTo(mutableListOf()) { n -> n.copy() }
    )

    override fun doCut(position: Double, whichHalf: HorizontalDirection, newName: String): ScoreObject {
        val notes = when (whichHalf) {
            LEFT -> notes.filter { n -> n.onset < position }
            RIGHT -> notes.filter { n -> n.onset >= position }
        }.mapTo(mutableListOf()) { n -> n.copy() }
        return PianoRollObject(
            reactiveVariable(newName), initialInstrument,
            lowestPitch, highestPitch,
            eventDictionary.clone(), notes
        )
    }

    override fun writeCode(env: ScorePlayEnv, name: String, cutoff: Double): String = code {
        val generalEventDict = eventDictionary.editor.result.now
        for ((idx, n) in notes.withIndex()) {
            val t = n.onset - cutoff
            if (t < -n.duration) continue
            val dur = n.duration + t.coerceAtMost(0.0)
            val midinote = n.midinote
            val eventDict = n.eventDictionary.editor.result.now
            val eventMap = mutableMapOf<String, String>()
            eventMap["duration"] = dur.toString()
            for ((key, value) in eventDict.entries) eventMap[key.text] = value.code(context)
            for ((key, value) in generalEventDict.entries) eventMap[key.text] = value.code(context)
            val playNote = when (val instr = instrument.get<InstrumentObject>()) {
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
                    eventMap["vst"] = instr.variableName
                    val namedValues = eventMap.entries.joinToString { (name, value) -> "$name: $value" }
                    "(type: \\vst_midi, vst: ${instr.variableName}, $namedValues).play"
                }
            }
            appendBlock("TempoClock.sched(${t.coerceAtLeast(0.0)})") {
                +playNote
            }
            appendLine(";")
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
            private val obj: PianoRollObject, private val position: Double, private val amount: Double
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
            private val obj: PianoRollObject, private val from: Double, private val to: Double
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
        private var _time: Double,
        private var _duration: Double,
        private var _midinote: Int,
        val eventDictionary: EditorRoot<EventDictionaryEditor>
    ) {
        @Transient
        lateinit var parent: PianoRollObject

        var onset: Double by this::_time.reactive { oldValue, newValue ->
            parent.context[UndoManager].record(PropertyEdit(this::onset, oldValue, newValue, "Edit note time"))
            parent.updateNote(this)
        }
        var duration: Double by this::_duration.reactive { oldValue, newValue ->
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
            fun create(context: Context, time: Double, duration: Double, midinote: Int): Note {
                val eventDictionary = EventDictionaryEditor(context)
                context.withoutUndo {
                    eventDictionary.entries.addLast(
                        NamedExprEditor(
                            context,
                            IdentifierEditor(context, "velocity"),
                            ScExprExpander(context, "60")
                        )
                    )
                }
                return Note(time, duration, midinote, EditorRoot.create(eventDictionary))
            }
        }
    }
}