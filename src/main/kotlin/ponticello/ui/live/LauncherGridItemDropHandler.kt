package ponticello.ui.live

import fxutils.drag.ConfiguredDropHandler
import javafx.scene.input.DragEvent
import javafx.scene.input.TransferMode
import ponticello.impl.Decimal
import ponticello.impl.zero
import ponticello.model.code.ScriptObject
import ponticello.model.flow.AudioFlow
import ponticello.model.live.ItemTarget
import ponticello.model.live.LauncherGrid
import ponticello.model.live.LauncherGrid.GridItemReference
import ponticello.model.live.LiveObject
import ponticello.model.obj.project
import ponticello.model.project.UI_STATE
import ponticello.model.project.get
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.registry.reference
import ponticello.model.score.ScoreObject
import ponticello.model.server.BufferObject
import ponticello.model.server.BufferRegistry
import ponticello.model.server.SampleObject
import ponticello.ui.record.LiveBuffersPane
import reaktive.value.reactiveVariable

class LauncherGridItemDropHandler(
    private val grid: LauncherGrid, private val item: LauncherGrid.GridItem,
) : ConfiguredDropHandler() {
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
            createPlayBufTarget(ev, buffer, item)
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
            createPlayBufTarget(ev, buffer, item)
            true
        }
    }

    private fun createPlayBufTarget(ev: DragEvent, buffer: BufferObject, item: LauncherGrid.GridItem) {
        val synthDef = grid.context.project[UI_STATE].getOrSelectInstrument(ev) ?: return
        val obj = buffer.createSoundProcess(synthDef) ?: return
        grid.context[ScoreObjectRegistry].add(obj)
        item.target = ItemTarget.Object(obj.reference(), yPosition = reactiveVariable(zero))
    }
}