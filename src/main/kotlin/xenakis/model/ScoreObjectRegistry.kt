package xenakis.model

import bundles.PublicProperty
import bundles.publicProperty
import reaktive.value.now

class ScoreObjectRegistry : ObjectRegistry<ScoreObject>() {
    override val objects: MutableList<ScoreObject> = mutableListOf()

    override val objectType: String
        get() = "Score object"

    override fun onAdded(obj: ScoreObject, idx: Int) {
        println("Added ${obj.name.now} to registry")
    }

    override fun onRemoved(obj: ScoreObject, idx: Int) {
        println("Removed ${obj.name.now} from registry")
    }

    override fun getDefault(): ScoreObject = error("No default score object")

    fun availableName(prefix: String): String {
        for (n in 1..Int.MAX_VALUE) {
            val name = "${prefix}$n"
            if (!has(name)) return name
        }
        throw AssertionError()
    }

    fun nameForCopy(obj: ScoreObject): String = availableName("${obj.name.now}_copy")

    fun nameForClone(obj: ScoreObject): String = availableName("${obj.name.now}_clone")

    companion object : PublicProperty<ScoreObjectRegistry> by publicProperty("ScoreObjectRegistry")
}