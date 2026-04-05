package ponticello.sc.editor

import javafx.scene.input.DataFormat
import ponticello.model.obj.project
import ponticello.model.project.objects
import ponticello.model.score.ScoreObject
import ponticello.model.score.SoundProcess
import ponticello.ui.dock.AppLayout
import ponticello.ui.score.ScoreObjectViewPane

class ScoreObjectSelector : ObjectSelector<ScoreObject>() {
    override fun filter(obj: ScoreObject): Boolean = when {
        parent is PlayObjectEditor -> obj is SoundProcess
        else -> true
    }

    override fun getOptions(): List<ScoreObject> = context.project.objects

    override fun dataFormat(): DataFormat = ScoreObject.DATA_FORMAT

    override val canViewSelected: Boolean
        get() = true

    override fun viewObject(obj: ScoreObject) {
        context[AppLayout].get<ScoreObjectViewPane>().showContent(obj)
    }
}