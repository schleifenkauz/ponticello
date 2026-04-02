package ponticello.sc.editor

import fxutils.prompt.PromptPlacement
import javafx.scene.input.DataFormat
import ponticello.model.obj.project
import ponticello.model.project.objects
import ponticello.model.score.ScoreObject
import ponticello.model.score.SoundProcess

class ScoreObjectSelector : ObjectSelector<ScoreObject>() {
    override fun filter(obj: ScoreObject): Boolean = when {
        parent is PlayObjectEditor -> obj is SoundProcess
        else -> true
    }

    override fun getOptions(): List<ScoreObject> = context.project.objects

    override fun dataFormat(): DataFormat = ScoreObject.DATA_FORMAT

    override fun createNewObject(name: String, promptPlacement: PromptPlacement): ScoreObject? = null
}