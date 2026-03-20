package ponticello.ui.score

import fxutils.drag.ConfiguredDropHandler
import fxutils.prompt.PromptPlacement
import fxutils.prompt.nextToTarget
import hextant.context.compoundEdit
import hextant.serial.readJson
import javafx.geometry.Point2D
import javafx.scene.input.TransferMode
import ponticello.impl.Logger
import ponticello.model.midi.MidiUtil
import ponticello.model.obj.project
import ponticello.model.project.UI_STATE
import ponticello.model.project.get
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.score.ObjectPosition
import ponticello.model.score.ScoreObject
import ponticello.model.score.ScoreObjectInstance
import ponticello.model.server.BufferObject
import ponticello.model.server.BufferRegistry
import ponticello.model.server.SampleObject
import ponticello.ui.midi.MidiTrackSelectorPrompt

class ScorePaneDropHandler(private val scorePane: ScorePane) : ConfiguredDropHandler() {
    val context get() = scorePane.context

    init {
        handleSingleFile(*SampleObject.SUPPORTED_AUDIO_FORMATS) { ev, file ->
            val buffer = context[BufferRegistry].getOrAdd(file)
            val pos = scorePane.snapToGrid(ev.x, ev.y)
            createPlayBufObject(buffer, pos, ev.nextToTarget(), scorePane)
            true
        }
        handleSingleFile("mid") { ev, file ->
            val anchor = Point2D(ev.screenX, ev.screenY)
            val ownerWindow = scorePane.scene.window
            val track = MidiTrackSelectorPrompt(context)
                .showPopup(anchor, ownerWindow) ?: return@handleSingleFile false
            context.compoundEdit("Import MIDI file") {
                val obj = MidiUtil.createMidiObjectFromFile(file, track, context) ?: return@handleSingleFile false
                val name = context[ScoreObjectRegistry].availableName(file.nameWithoutExtension)
                obj.setInitialName(name)
                val pos = scorePane.snapToGrid(ev.x, ev.y)
                val inst = ScoreObjectInstance(obj, pos)
                scorePane.score.addObject(inst, autoSelect = true)
                true
            }
        }
        handleTypedFormat(BufferObject.DATA_FORMAT, TransferMode.COPY) { ev, ref ->
            val buffer = ref.resolve(context[BufferRegistry]) ?: return@handleTypedFormat false
            val pos = scorePane.snapToGrid(ev.x, ev.y)
            createPlayBufObject(buffer, pos, ev.nextToTarget(), scorePane)
            true
        }
        handleTypedFormat(ScoreObject.DATA_FORMAT, TransferMode.COPY) { ev, ref ->
            val obj = ref.resolve(context[ScoreObjectRegistry]) ?: return@handleTypedFormat false
            val pos = scorePane.snapToGrid(ev.x, ev.y)
            val inst = ScoreObjectInstance(obj, pos)
            scorePane.score.addObject(inst, autoSelect = true)
            true
        }
        handleSingleFile("obj.json") { ev, file ->
            val name = file.nameWithoutExtension
            if (context[ScoreObjectRegistry].has(name = name)) {
                Logger.error("ScoreObject with name $name already exists")
                return@handleSingleFile false
            }
            val obj = try {
                file.readJson<ScoreObject>()
            } catch (e: Exception) {
                Logger.error("Error reading $file", e)
                return@handleSingleFile false
            }
            val pos = scorePane.snapToGrid(ev.x, ev.y)
            val inst = ScoreObjectInstance(obj, pos)
            scorePane.score.addObject(inst, autoSelect = true)
            true
        }
    }

    companion object {
        fun createPlayBufObject(
            buffer: BufferObject, position: ObjectPosition, promptPlacement: PromptPlacement, scorePane: ScorePane,
        ) {
            val instrument = scorePane.context.project[UI_STATE].getOrSelectInstrument(promptPlacement) ?: return
            val obj = buffer.createSoundProcess(instrument) ?: return
            val inst = ScoreObjectInstance(obj, position)
            scorePane.context.compoundEdit("Add sample to score") {
                scorePane.score.addObject(inst, autoSelect = true)
            }
        }
    }
}