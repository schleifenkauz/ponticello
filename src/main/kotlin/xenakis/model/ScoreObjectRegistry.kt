package xenakis.model

import bundles.PublicProperty
import bundles.publicProperty

class ScoreObjectRegistry : ObjectRegistry<ScoreObject>() {
    override val objects: MutableList<ScoreObject> = mutableListOf()

    override val objectType: String
        get() = "Score object"

    override fun getDefault(): ScoreObject = error("No default score object")

    override fun onAdded(obj: ScoreObject, idx: Int) {}



    companion object : PublicProperty<ScoreObjectRegistry> by publicProperty("ScoreObjectRegistry")
}