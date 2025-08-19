package ponticello.model.score

import fxutils.undo.AbstractEdit
import fxutils.undo.PropertyEdit
import fxutils.undo.UndoManager
import hextant.context.Context
import hextant.context.withoutUndo
import hextant.serial.EditorRoot
import javafx.geometry.HorizontalDirection
import javafx.geometry.HorizontalDirection.LEFT
import javafx.geometry.HorizontalDirection.RIGHT
import javafx.geometry.Side
import javafx.scene.paint.Color
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.*
import ponticello.model.flow.NodePlacement
import ponticello.model.obj.MidiInstrument
import ponticello.model.obj.ParameterDefObject
import ponticello.model.score.controls.ParameterControl
import ponticello.sc.EventDictionary
import ponticello.sc.code
import ponticello.sc.editor.*
import ponticello.ui.score.LegacyMidiObjectView
import reaktive.value.*
import reaktive.value.binding.flatMap
import reaktive.value.binding.orElse
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

@Serializable
@SerialName("ponticello.model.score.MidiObject")
class LegacyMidiObject(
    val instrument: ReactiveVariable<MidiInstrument>,
    @SerialName("lowestPitch") private var _lowestPitch: Int,
    @SerialName("highestPitch") private var _highestPitch: Int,
    val eventDictionary: EditorRoot<@Contextual EventDictionaryEditor>,
    val notes: MutableList<Note>,
    val latencyMs: ReactiveVariable<Int> = reactiveVariable(0),
) : ScoreObject() {
    override val type: String
        get() = "midi"

    override val superColliderPrefix: String
        get() = "~midi_"

    var lowestPitch
        get() = _lowestPitch
        set(value) {
            _lowestPitch = value
            notifyListeners<LegacyMidiObjectView> { updatedPitchRange() }
        }

    var highestPitch
        get() = _highestPitch
        set(value) {
            _highestPitch = value
            notifyListeners<LegacyMidiObjectView> { updatedPitchRange() }
        }

    val pitchRange get() = lowestPitch..highestPitch

    override val associatedColor: ReactiveValue<Color?>
        get() = super.associatedColor.orElse(instrument.flatMap { instr ->
            if (instr is MidiInstrument.SynthDef) instr.reference.get()?.color ?: reactiveValue(null)
            else reactiveValue(null)
        })

    @Transient
    private var pixelsPerPitch: Double = -1.0

    override fun initialize(context: Context) {
        if (initialized) return
        super.initialize(context)
        instrument.now.resolve(context)
        eventDictionary.initialize(context)
        for (note in notes) {
            note.parent = this
            note.initialize(context)
        }
    }

    override fun beginResize(mode: ResizeMode, side: Side): Boolean {
        pixelsPerPitch = (height / (highestPitch - lowestPitch + 1)).value
        return super.beginResize(mode, side)
    }

    override fun resize(targetDuration: Decimal, targetHeight: Decimal) {
        if (resizeMode!!.isStretch) {
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
                minDur = when (resizeSide) {
                    Side.LEFT -> this.duration - notes.minOf { n -> n.onset }
                    Side.RIGHT -> notes.maxOf { o -> o.onset + o.duration }
                    else -> minDur
                }

                minHeight = when (resizeSide) {
                    Side.BOTTOM -> this.height - notes.minOf { n -> pixelsPerPitch * (n.midinote - lowestPitch) }
                    Side.TOP -> notes.maxOf { n ->
                        (pixelsPerPitch * (n.midinote - lowestPitch)) + pixelsPerPitch
                    }.asY

                    else -> minHeight
                }
            }
            val deltaDur = targetDuration.coerceAtLeast(minDur) - this.duration
            val deltaHeight = targetHeight.coerceAtLeast(minHeight) - this.height
            val pitches = ((this.height + deltaHeight) / pixelsPerPitch).ceilToInt()
            if (pitches != pitchRange.count()) {
                if (resizeSide == Side.TOP) highestPitch = lowestPitch + pitches
                else if (resizeSide == Side.BOTTOM) lowestPitch = highestPitch - pitches
            }
            super.resize(this.duration + deltaDur, (pitches * pixelsPerPitch).withPrecision(ObjectPosition.Y_PRECISION))
            if (resizeSide == Side.LEFT) {
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
        if (!note.isInitialized) note.initialize(context)
        notes.add(note)
        note.parent = this
        context[UndoManager].record(Edit.AddNote(this, note))
        notifyListeners<LegacyMidiObjectView> { addedNote(note) }
    }

    fun removeNote(note: Note) {
        notes.remove(note)
        context[UndoManager].record(Edit.RemoveNote(this, note))
        notifyListeners<LegacyMidiObjectView> { removedNote(note) }
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
        notifyListeners<LegacyMidiObjectView> { updatedNote(note) }
    }

    override fun addListener(view: Listener) {
        super.addListener(view)
        if (view is LegacyMidiObjectView) {
            for (note in notes) {
                view.addedNote(note)
            }
        }
    }

    override fun doClone(): ScoreObject = LegacyMidiObject(
        instrument.copy(), lowestPitch, highestPitch,
        eventDictionary.clone(),
        notes.mapTo(mutableListOf()) { n -> n.copy() }
    )

    override fun doCut(position: Decimal, whichHalf: HorizontalDirection): ScoreObject {
        val notes = when (whichHalf) {
            LEFT -> notes.filter { n -> n.onset < position }
            RIGHT -> notes.filter { n -> n.onset >= position }
        }.mapTo(mutableListOf()) { n -> n.copy() }
        return LegacyMidiObject(
            instrument,
            lowestPitch, highestPitch,
            eventDictionary.clone(context), notes
        )
    }

    override fun writeCode(
        instance: ScoreObjectInstance?,
        uniqueName: String,
        placement: NodePlacement?,
        cutoff: Decimal,
        latency: Decimal,
        extraArguments: Map<ParameterDefObject, ParameterControl>,
    ): String = when (val instr = instrument.now) {
        is MidiInstrument.SynthDef -> writeCode {
            val generalEventDict = eventDictionary.editor.result.now
            val groupVar = "~midi_$uniqueName"
            +"s.sync"
            +"$groupVar = Group.new(${placement!!.code})"
            for (n in notes) {
                val onset = n.onset - cutoff
                if (onset + n.duration <= zero) continue
                val dur = n.duration + n.onset.coerceAtMost(zero)
                val midinote = n.midinote
                val eventDict = n.eventDictionary.editor.result.now
                val eventMap = mutableMapOf<String, String>()
                eventMap["duration"] = dur.toString()
                makeEventMap(eventDict, eventMap, generalEventDict)
                eventMap["freq"] = "$midinote.midicps + ${eventMap["detune"] ?: 0}.midiratio"
                eventMap["amp"] = "((${eventMap["velocity"] ?: 60}) / 127).pow(2)"
                eventMap.remove("detune")
                val namedValues = eventMap.entries.joinToString { (name, value) -> "$name: $value" }
                val synthDefName = instr.reference.get()?.name?.now
                appendBlock("TempoClock.sched(${onset.coerceAtLeast(zero)})") {
                    appendBlock("if ($groupVar != nil)") {
                        appendBlock("s.makeBundle($latency)") {
                            +"Synth.tail($groupVar, \\$synthDefName, [${namedValues}])"
                        }
                    }
                }
            }
            appendBlock("TempoClock.sched(${duration - cutoff})") {
                +"var group = $groupVar"
                +"$groupVar = nil"
                appendBlock("s.makeBundle($latency)") {
                    +"group.release(1)"
                }
                appendBlock("TempoClock.sched(1 + $latency)") {
                    +"group.free"
                }
            }
        }

        is MidiInstrument.VST -> writeCode {
            val controllerVar = instr.flow.get()?.controllerVar
            val helperVariable = "~midi_$uniqueName"
            +"$helperVariable = ()"
            for (n in notes) {
                val onset = n.onset - cutoff
                if (onset + n.duration <= zero) continue
                val midinote = n.midinote
                val eventMap = mutableMapOf<String, String>()
                makeEventMap(n.eventDictionary.editor.result.now, eventMap, eventDictionary.editor.result.now)
                val velocity = eventMap["velocity"] ?: "64"
                val combinedLatency = latency - (latencyMs.now / 1000.0)
                appendBlock("TempoClock.sched(${onset.coerceAtLeast(zero) + combinedLatency})") {
                    appendBlock("if ($helperVariable != nil)") {
                        +"$controllerVar.midi.noteOn(0, $midinote, $velocity)"
                    }
                }
                appendBlock("TempoClock.sched(${onset + n.duration + combinedLatency})") {
                    appendBlock("if ($helperVariable != nil)") {
                        +"$controllerVar.midi.noteOff(0, $midinote)"
                    }
                }
            }
        }

        MidiInstrument.None -> {
            Logger.warn("No instrument selected for ${name.now}", Logger.Category.Playback)
            ""
        }
    }

    private fun makeEventMap(
        eventDict: EventDictionary,
        eventMap: MutableMap<String, String>,
        generalEventDict: EventDictionary,
    ) {
        for ((key, value) in eventDict.entries) eventMap[key.text] = value.code(context)
        for ((key, value) in generalEventDict.entries) eventMap[key.text] = value.code(context)
    }

    abstract class Edit(protected val obj: LegacyMidiObject, protected val note: Note) : AbstractEdit() {
        class AddNote(obj: LegacyMidiObject, note: Note) : Edit(obj, note) {
            override val actionDescription: String
                get() = "Add note"

            override fun doUndo() {
                obj.removeNote(note)
            }

            override fun doRedo() {
                obj.addNote(note)
            }
        }

        class RemoveNote(obj: LegacyMidiObject, note: Note) : Edit(obj, note) {
            override val actionDescription: String
                get() = "Remove note"

            override fun doUndo() {
                obj.addNote(note)
            }

            override fun doRedo() {
                obj.removeNote(note)
            }
        }

        class Transpose(private val obj: LegacyMidiObject, private val deltaPitch: Int) : AbstractEdit() {
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
            private val obj: LegacyMidiObject, private val position: Decimal, private val amount: Decimal,
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
            private val obj: LegacyMidiObject, private val from: Decimal, private val to: Decimal,
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
        val eventDictionary: EditorRoot<@Contextual EventDictionaryEditor>,
    ) {
        @Transient
        lateinit var parent: LegacyMidiObject

        var onset: Decimal by this::_time.reactive { oldValue, newValue ->
            parent.context[UndoManager].record(PropertyEdit(this::onset, oldValue, newValue, "Edit note onset"))
            parent.updateNote(this)
        }
        var duration: Decimal by this::_duration.reactive { oldValue, newValue ->
            parent.context[UndoManager].record(PropertyEdit(this::duration, oldValue, newValue, "Edit note duration"))
            parent.updateNote(this)
        }
        var midinote: Int by this::_midinote.reactive { oldValue, newValue ->
            parent.context[UndoManager].record(PropertyEdit(this::midinote, oldValue, newValue, "Edit pitch"))
            parent.updateNote(this)
        }

        val isInitialized get() = eventDictionary.editor.isInitialized

        override fun toString(): String = "Note(start: $onset, dur: $duration, pitch: $midinote)"

        fun copy() = Note(onset, duration, midinote, eventDictionary.clone())

        fun initialize(context: Context) {
            eventDictionary.initialize(context)
        }

        companion object {
            fun create(time: Decimal, duration: Decimal, midinote: Int): Note {
                val eventDictionary = EventDictionaryEditor(
                    NamedExprListEditor(
                        NamedExprEditor(
                            IdentifierEditor("velocity"),
                            ScExprExpander("60")
                        )
                    )
                )
                return Note(time, duration, midinote, EditorRoot(eventDictionary))
            }
        }
    }
}