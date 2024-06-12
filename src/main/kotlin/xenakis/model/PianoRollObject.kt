package xenakis.model

import hextant.context.Context
import hextant.context.withoutUndo
import hextant.core.editor.ListenerManager
import hextant.serial.EditorRoot
import hextant.serial.SnapshotAware
import hextant.undo.AbstractEdit
import hextant.undo.PropertyEdit
import hextant.undo.UndoManager
import javafx.geometry.HorizontalDirection
import javafx.geometry.HorizontalDirection.LEFT
import javafx.geometry.HorizontalDirection.RIGHT
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put
import reaktive.value.now
import xenakis.impl.*
import xenakis.sc.code
import xenakis.sc.editor.*
import xenakis.ui.PianoRollObjectView
import xenakis.ui.ScoreObjectView
import xenakis.ui.format
import kotlin.math.roundToInt

class PianoRollObject(
    name: String,
    private val initialInstrument: InstrumentObject.Reference,
    lowestPitch: Int, highestPitch: Int,
    val eventDictionary: EditorRoot<EventDictionaryEditor>,
    private val notes: MutableList<Note>
) : RegularScoreObject(name) {
    override val type: String
        get() = "piano-roll"

    override val viewManager: ListenerManager<PianoRollObjectView> = ListenerManager.createWeakListenerManager()

    lateinit var instrumentSelector: InstrumentSelector
        private set

    val instrument get() = if (initialized) instrumentSelector.result.now else initialInstrument

    var lowestPitch = lowestPitch
        set(value) {
            field = value
            viewManager.notifyListeners { updatedPitchRange() }
        }

    var highestPitch = highestPitch
        set(value) {
            field = value
            viewManager.notifyListeners { updatedPitchRange() }
        }

    val pitchRange get() = lowestPitch..highestPitch

    val pixelsPerPitch get() = height / (highestPitch - lowestPitch)

    fun notes(): List<Note> = notes

    override fun initialize(context: Context) {
        if (initialized) return
        super.initialize(context)
        instrumentSelector = InstrumentSelector(context, initialInstrument)
        for (note in notes) {
            note.parent = this
        }
    }

    fun addTime(position: Double, amount: Double) {
        require(position in 0.0..duration) { "Invalid position $position not in 0..$duration" }
        for (note in notes) {
            if (note.time >= position) note.time += amount
        }
        duration += amount
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

    override fun copy(): ScoreObject = PianoRollObject(
        name.now, instrument, lowestPitch, highestPitch,
        context.withoutUndo { eventDictionary.clone() },
        notes.mapTo(mutableListOf()) { n -> n.copy() }
    )

    override fun cut(position: Double, whichHalf: HorizontalDirection): ScoreObject {
        val notes = when (whichHalf) {
            LEFT -> notes.filter { n -> n.time < position }
            RIGHT -> notes.filter { n -> n.time >= position }
        }.mapTo(mutableListOf()) { n -> n.copy() }
        return PianoRollObject(name.now, initialInstrument, lowestPitch, highestPitch, eventDictionary.clone(), notes)
    }

    override fun writeCode(writer: ScWriter, playAt: Double, name: String) {
        val generalEventDict = eventDictionary.editor.result.now
        for (n in notes) {
            val t = playAt + n.time
            if (t < -duration) continue
            val offset = -t.coerceAtMost(0.0)
            val dur = n.duration - offset
            val midinote = n.midinote
            val eventDict = n.eventDictionary.editor.result.now
            val eventMap = mutableMapOf<String, String>()
            eventMap["duration"] = dur.format(3)
            for ((key, value) in eventDict.entries) eventMap[key.text] = value.code
            for ((key, value) in generalEventDict.entries) eventMap[key.text] = value.code
            when (val instr = instrument.get()) {
                is SynthDefObject -> {
                    eventMap["freq"] = "$midinote.midicps + ${eventMap["detune"] ?: 0}.midiratio"
                    eventMap.remove("detune")
                    val namedValues = eventMap.entries.joinToString { (name, value) -> "$name: $value" }
                    writer.appendLine("s.makeBundle($t) { Synth(\\${instr.name.now}, [${namedValues}]) };")
                }

                is VSTPluginObject -> {
                    eventMap["midinote"] = midinote.toString()
                    eventMap["type"] = "\\vst_midi"
                    eventMap["vst"] = instr.variableName
                    val namedValues = eventMap.entries.joinToString { (name, value) -> "$name: $value" }
                    writer.appendLine("SystemClock.sched($t) { (type: \\vst_midi, vst: ${instr.variableName}, $namedValues).play };")
                }
            }
        }
    }

    override fun JsonObjectBuilder.saveToJson() {
        putSerializableValue("instrument", instrument)
        put("lowestPitch", lowestPitch)
        put("highestPitch", highestPitch)
        putSerializableValue("eventDictionary", eventDictionary)
        putSerializableValue("notes", notes as List<Note>)
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

        var time: Double by this::_time.reactive { oldValue, newValue ->
            parent.context[UndoManager].record(PropertyEdit(this::time, oldValue, newValue, "Edit note time"))
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

        override fun toString(): String = "Note(start: $time, dur: $duration, pitch: $midinote)"

        fun copy() = Note(time, duration, midinote, eventDictionary.clone())

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

    object Serializer : ScoreObject.Serializer {
        override val type: String
            get() = "piano-roll"

        override fun JsonObject.createFromJson(name: String): ScoreObject {
            val instrument = getSerializableValue<InstrumentObject.Reference>("instrument")!!
            val lowestPitch = getInt("lowestPitch")!!
            val highestPitch = getInt("highestPitch")!!
            SnapshotAware.Serializer.reconstructionContext.withoutUndo {
                val eventDictionary = getSerializableValue<EditorRoot<EventDictionaryEditor>>("eventDictionary")!!
                val notes = getSerializableValue<List<Note>>("notes")!!.toMutableList()
                return PianoRollObject(name, instrument, lowestPitch, highestPitch, eventDictionary, notes)
            }
        }
    }
}