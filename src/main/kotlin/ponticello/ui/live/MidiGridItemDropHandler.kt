package ponticello.ui.live

import fxutils.drag.ConfiguredDropHandler
import fxutils.prompt.PromptPlacement
import fxutils.prompt.nextToTarget
import javafx.scene.input.TransferMode
import ponticello.impl.Decimal
import ponticello.impl.json
import ponticello.impl.zero
import ponticello.model.code.ScriptObject
import ponticello.model.flow.AudioFlow
import ponticello.model.live.GridItem
import ponticello.model.live.ItemTarget
import ponticello.model.live.LiveObject
import ponticello.model.midi.MidiGridInstrument
import ponticello.model.midi.MidiGridInstrument.GridItemReference
import ponticello.model.obj.project
import ponticello.model.project.UI_STATE
import ponticello.model.project.get
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.registry.reference
import ponticello.model.score.ScoreBreakpointObject
import ponticello.model.score.ScoreObject
import ponticello.model.server.BufferObject
import ponticello.model.server.BufferRegistry
import ponticello.model.server.SampleObject
import ponticello.ui.actions.PlaybackActions
import ponticello.ui.record.LiveBuffersPane
import reaktive.value.reactiveVariable

class MidiGridItemDropHandler(
    private val grid: MidiGridInstrument, private val item: GridItem,
) : ConfiguredDropHandler(json) {
    init {
        handleTypedFormat(GridItemReference.DATA_FORMAT, TransferMode.MOVE) { _, ref ->
            val droppedItem = ref.getItem(grid)
            grid.swap(item, droppedItem)
            true
        }
        handleTypedFormat(LiveBuffersPane.TOGGLE_RECORD, TransferMode.LINK) { _, ref ->
            item.target = ItemTarget.ToggleRecording(ref)
            true
        }
        handleTypedFormat(PlaybackActions.DATA_FORMAT, TransferMode.LINK) { _, action ->
            item.target = ItemTarget.PlaybackAction(action)
            true
        }
        handleTypedFormat(ScoreBreakpointObject.DATA_FORMAT, TransferMode.LINK) { _, ref ->
            item.target = ItemTarget.Breakpoint(ref)
            true
        }
        handleTypedFormat(AudioFlow.DATA_FORMAT, TransferMode.LINK) { _, ref ->
            item.target = ItemTarget.Flow(ref)
            true
        }
        handleTypedFormat(ScoreObject.DATA_FORMAT, TransferMode.LINK) { ev, ref ->
            val scoreY = ev.dragboard.getContent(ScoreObject.ABSOLUTE_SCORE_Y) as? Decimal ?: zero
            item.target = ItemTarget.Object(ref, reactiveVariable(scoreY))
            true
        }
        handleTypedFormat(BufferObject.DATA_FORMAT, TransferMode.LINK) { ev, ref ->
            ref.resolve(grid.context[BufferRegistry])
            val buffer = ref.get() ?: return@handleTypedFormat false
            item.target = createPlayBufTarget(ev.nextToTarget(), buffer) ?: return@handleTypedFormat false
            true
        }
        handleTypedFormat(ScriptObject.DATA_FORMAT, TransferMode.LINK) { _, ref ->
            item.target = ItemTarget.Script(ref)
            true
        }
        handleTypedFormat(LiveObject.DATA_FORMAT, TransferMode.LINK) { _, ref ->
            item.target = ItemTarget.LiveObjectRef(ref)
            true
        }
        handleSingleFile(*SampleObject.SUPPORTED_AUDIO_FORMATS) { ev, file ->
            val buffer = grid.context[BufferRegistry].getOrAdd(file)
            item.target = createPlayBufTarget(ev.nextToTarget(), buffer) ?: return@handleSingleFile false
            true
        }
        handleTypedFormat(ItemTarget.DATA_FORMAT, TransferMode.LINK) { _, target ->
            item.target = target
            true
        }
    }

    private fun createPlayBufTarget(promptPlacement: PromptPlacement, buffer: BufferObject): ItemTarget.Object? {
        val synthDef = grid.context.project[UI_STATE].getOrSelectInstrument(promptPlacement) ?: return null
        val obj = buffer.createSoundProcess(synthDef) ?: return null
        grid.context[ScoreObjectRegistry].add(obj)
        return ItemTarget.Object(obj.reference(), yPosition = reactiveVariable(zero))
    }
}