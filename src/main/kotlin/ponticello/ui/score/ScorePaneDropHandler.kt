package ponticello.ui.score

import fxutils.drag.ConfiguredDropHandler
import hextant.context.compoundEdit
import javafx.event.Event
import javafx.scene.input.TransferMode
import ponticello.model.obj.BufferObject
import ponticello.model.obj.SampleObject
import ponticello.model.obj.project
import ponticello.model.project.UI_STATE
import ponticello.model.project.get
import ponticello.model.registry.BufferRegistry
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.score.ObjectPosition
import ponticello.model.score.ScoreObject
import ponticello.model.score.ScoreObjectInstance

class ScorePaneDropHandler(private val scorePane: ScorePane) : ConfiguredDropHandler() {
    init {
        handleSingleFile(*SampleObject.SUPPORTED_AUDIO_FORMATS) { ev, file ->
            val buffer = scorePane.context[BufferRegistry].getOrAdd(file)
            val pos = scorePane.snapToGrid(ev.x, ev.y)
            createPlayBufObject(buffer, pos, ev, scorePane)
            true
        }
        handleTypedFormat(BufferObject.DATA_FORMAT, TransferMode.COPY) { ev, ref ->
            val buffer = ref.resolve(scorePane.context[BufferRegistry]) ?: return@handleTypedFormat false
            val pos = scorePane.snapToGrid(ev.x, ev.y)
            createPlayBufObject(buffer, pos, ev, scorePane)
            true
        }
        handleTypedFormat(ScoreObject.DATA_FORMAT, TransferMode.COPY) { ev, ref ->
            val obj = ref.resolve(scorePane.context[ScoreObjectRegistry]) ?: return@handleTypedFormat false
            val pos = scorePane.snapToGrid(ev.x, ev.y)
            val inst = ScoreObjectInstance(obj, pos)
            scorePane.score.addObject(inst, autoSelect = true)
            true
        }
    }

    companion object {
        fun createPlayBufObject(
            buffer: BufferObject, position: ObjectPosition, ev: Event?, scorePane: ScorePane,
        ) {
            val instrument = scorePane.context.project[UI_STATE].getOrSelectInstrument(ev) ?: return
            val obj = buffer.createSynthObject(instrument) ?: return
            val inst = ScoreObjectInstance(obj, position)
            scorePane.context.compoundEdit("Add sample to score") {
                scorePane.score.addObject(inst, autoSelect = true)
            }
        }
    }
}