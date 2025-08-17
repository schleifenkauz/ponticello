package ponticello.ui.live

import fxutils.drag.ConfiguredDropHandler
import javafx.scene.input.DragEvent
import ponticello.model.flow.AudioFlow
import ponticello.model.live.ItemTarget
import ponticello.model.live.LauncherGrid
import ponticello.model.live.LauncherGrid.GridItemReference
import ponticello.model.live.LiveTaskObject
import ponticello.model.obj.BufferObject
import ponticello.model.obj.SampleObject
import ponticello.model.obj.ScriptObject
import ponticello.model.obj.project
import ponticello.model.project.UI_STATE
import ponticello.model.project.get
import ponticello.model.registry.BufferRegistry
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.registry.reference
import ponticello.model.score.ScoreObject
import ponticello.ui.actions.PlaybackActions

class LauncherGridItemDropHandler(
    private val grid: LauncherGrid, private val item: LauncherGrid.GridItem,
) : ConfiguredDropHandler() {
    init {
        handleTypedFormat(GridItemReference.DATA_FORMAT) { _, ref ->
            val droppedItem = ref.getItem(grid)
            grid.swap(item, droppedItem)
            true
        }
        handleFormat(PlaybackActions.RECORD_BUTTON) { _, _ ->
            item.target = ItemTarget.ToggleRecording
            true
        }
        handleTypedFormat(AudioFlow.DATA_FORMAT) { _, ref ->
            item.target = ItemTarget.Flow(ref)
            true
        }
        handleTypedFormat(ScoreObject.DATA_FORMAT) { _, ref ->
            item.target = ItemTarget.Object(ref)
            true
        }
        handleTypedFormat(BufferObject.DATA_FORMAT) { ev, ref ->
            ref.resolve(grid.context[BufferRegistry])
            val buffer = ref.get() ?: return@handleTypedFormat false
            createPlayBufTarget(ev, buffer, item)
            true
        }
        handleTypedFormat(ScriptObject.DATA_FORMAT) { _, ref ->
            item.target = ItemTarget.Script(ref)
            true
        }
        handleTypedFormat(LiveTaskObject.DATA_FORMAT) { _, ref ->
            item.target = ItemTarget.LiveTask(ref)
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
        val obj = buffer.createSynthObject(synthDef) ?: return
        grid.context[ScoreObjectRegistry].add(obj)
        item.target = ItemTarget.Object(obj.reference())
    }
}