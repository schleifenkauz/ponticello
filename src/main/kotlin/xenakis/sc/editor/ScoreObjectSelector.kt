package xenakis.sc.editor

import xenakis.model.registry.ObjectRegistry
import xenakis.model.registry.ScoreObjectRegistry
import xenakis.model.score.ScoreObject
import xenakis.model.score.SynthObject

class ScoreObjectSelector : ObjectSelector<ScoreObject>() {
    override fun filter(obj: ScoreObject): Boolean = when {
        parent is PlayObjectEditor -> obj is SynthObject
        else -> true
    }

    override fun getList(): ObjectRegistry<ScoreObject> = context[ScoreObjectRegistry]

    override fun createNewObject(name: String): ScoreObject? = null
}