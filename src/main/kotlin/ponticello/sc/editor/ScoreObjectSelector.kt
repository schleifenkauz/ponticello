package ponticello.sc.editor

import javafx.scene.input.DataFormat
import ponticello.model.obj.project
import ponticello.model.project.objects
import ponticello.model.registry.ObjectRegistry
import ponticello.model.score.ScoreObject
import ponticello.model.score.SoundProcess

class ScoreObjectSelector : ObjectSelector<ScoreObject>() {
    override fun filter(obj: ScoreObject): Boolean = when {
        parent is PlayObjectEditor -> obj is SoundProcess
        else -> true
    }

    override fun getList(): ObjectRegistry<ScoreObject> = context.project.objects

    override fun dataFormat(): DataFormat = ScoreObject.DATA_FORMAT

    override fun createNewObject(name: String): ScoreObject? = null
}