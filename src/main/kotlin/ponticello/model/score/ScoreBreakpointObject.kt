package ponticello.model.score

import fxutils.drag.TypedDataFormat
import javafx.scene.paint.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ponticello.impl.Decimal
import ponticello.impl.one
import ponticello.model.flow.AudioFlows
import ponticello.model.live.ItemTarget
import ponticello.model.midi.MidiGridInstrument
import ponticello.model.obj.project
import ponticello.model.player.ObjectPlaybackInfo
import ponticello.model.project.PLAYBACK_SETTINGS
import ponticello.model.project.get
import ponticello.model.registry.ObjectReference
import ponticello.model.registry.reference
import ponticello.sc.client.ScWriter
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveValue

@Serializable
@SerialName("Breakpoint")
class ScoreBreakpointObject : ScoreObject() {
    override var _name: ReactiveVariable<String>? = null

    override val type: String get() = "Breakpoint"

    override val canDuplicate: Boolean get() = false

    override val askBeforeDeleting: Boolean get() = false
    override val canResizeVertically: Boolean get() = false
    override val canResizeHorizontally: Boolean get() = false

    override val associatedColor: ReactiveValue<Color?>
        get() = reactiveValue(Color.BLUE)

    override var duration: Decimal
        get() {
            if (!initialized) return one
            val settings = context.project[PLAYBACK_SETTINGS]
            return settings.scLangLatency.now + settings.serverLatency.now - settings.extraLatency.now
        }
        set(value) {
            throw UnsupportedOperationException("Breakpoint objects cannot be resized")
        }

    override var height: Decimal
        get() = one
        set(value) {
            throw UnsupportedOperationException("Breakpoint objects cannot be resized")
        }

    override fun createInSuperCollider(writer: ScWriter) {
    }

    override fun ScWriter.startNewInstance(info: ObjectPlaybackInfo) {
    }

    override fun doClone(): ScoreObject = ScoreBreakpointObject()

    override fun onRename(oldName: String, newName: String) {
        super.onRename(oldName, newName)
        val midiGrids = context[AudioFlows].allMidiTracks().flatMap { t ->
            t.instruments.filterIsInstance<MidiGridInstrument>()
        }
        for (grid in midiGrids) {
            for (item in grid.allItems()) {
                val target = item.target
                if (target is ItemTarget.Breakpoint && target.ref.get() == this) {
                    item.target = ItemTarget.Breakpoint(reference())
                }
            }
        }
    }

    fun getPositionInScore(score: Score): Decimal? = score.instancesOf(this).singleOrNull()?.start

    companion object {
        val DATA_FORMAT = TypedDataFormat<ObjectReference<ScoreBreakpointObject>>("ponticello:breakpoint")
    }
}