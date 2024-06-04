package xenakis.model

import hextant.context.Context
import hextant.core.editor.ListenerManager
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
import xenakis.sc.editor.InstrumentSelector
import xenakis.ui.PianoRollObjectView
import xenakis.ui.ScoreObjectView
import xenakis.ui.format
import kotlin.math.roundToInt

class PianoRollObject(
    name: String,
    private val initialInstrument: InstrumentObject.Reference,
    private var lowestPitch: Int, private var highestPitch: Int,
    private val notes: MutableList<Note>
) : RegularScoreObject(name) {
    override val type: String
        get() = "piano-roll"

    override val viewManager: ListenerManager<PianoRollObjectView> = ListenerManager.createWeakListenerManager()

    lateinit var instrumentSelector: InstrumentSelector
        private set

    val instrument get() = instrumentSelector.result.now

    val pitchRange get() = lowestPitch..highestPitch

    val pixelsPerPitch get() = height / (highestPitch - lowestPitch)

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

    fun setLowestPitch(pitch: Int) {
        lowestPitch = pitch
        viewManager.notifyListeners { updatedPitchRange() }
    }

    fun setHighestPitch(pitch: Int) {
        highestPitch = pitch
        viewManager.notifyListeners { updatedPitchRange() }
    }

    fun getY(pitch: Int) = (highestPitch - pitch) * pixelsPerPitch

    fun getMidiNote(y: Double): Int = ((height - y) / pixelsPerPitch).roundToInt() + lowestPitch

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
        notes.mapTo(mutableListOf()) { n -> n.copy() }
    )

    override fun cut(position: Double, whichHalf: HorizontalDirection): ScoreObject {
        val notes = when (whichHalf) {
            LEFT -> notes.filter { n -> n.time < position }
            RIGHT -> notes.filter { n -> n.time >= position }
        }.mapTo(mutableListOf()) { n -> n.copy() }
        return PianoRollObject(name.now, initialInstrument, lowestPitch, highestPitch, notes)
    }

    override fun writeStartCode(writer: ScWriter, offset: Double, name: String) {
        writer.appendBlock("~play_$name = Task") {
            var currentTime = 0.0
            for (n in notes.sortedBy { n -> n.time }) {
                val t = n.time - offset
                if (t < 0.0) continue
                val dur = n.duration
                val midinote = n.midinote
                val velocity = n.velocity
                val deltaTime = (t - currentTime)
                +"$deltaTime.wait"
                val eventMap = instrument.get().createEvent().toMutableMap()
                eventMap["duration"] = dur.format(3)
                eventMap["midinote"] = midinote.toString()
                eventMap["velocity"] = velocity.toString()
                +eventMap.entries.joinToString(", ", "(", ").play") { (name, value) -> "$name: $value" }
                currentTime = t
            }
        }
        writer.appendLine(".play;")
    }

    override fun writeStopCode(writer: ScWriter, name: String) {
        writer.appendLine("~play_$name.stop;")
    }

    override fun JsonObjectBuilder.saveToJson() {
        putSerializableValue("instrument", instrument)
        put("lowestPitch", lowestPitch)
        put("highestPitch", highestPitch)
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
    }

    @Serializable
    class Note(
        private var _time: Double,
        private var _duration: Double,
        private var _midinote: Int,
        private var _velocity: Int
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
        var velocity: Int by this::_velocity.reactive { oldValue, newValue ->
            parent.context[UndoManager].record(PropertyEdit(this::velocity, oldValue, newValue, "Edit note time"))
            parent.updateNote(this)
        }

        override fun toString(): String = "Note(start: $time, dur: $duration, pitch: $midinote, velocity: $velocity)"

        fun copy() = Note(time, duration, midinote, velocity)
    }

    object Serializer : ScoreObject.Serializer {
        override val type: String
            get() = "piano-roll"

        override fun JsonObject.createFromJson(name: String): ScoreObject {
            val instrument = getSerializableValue<InstrumentObject.Reference>("instrument")!!
            val lowestPitch = getInt("lowestPitch")!!
            val highestPitch = getInt("highestPitch")!!
            val notes = getSerializableValue<List<Note>>("notes")!!.toMutableList()
            return PianoRollObject(name, instrument, lowestPitch, highestPitch, notes)
        }
    }
}