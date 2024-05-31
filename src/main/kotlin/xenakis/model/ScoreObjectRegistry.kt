package xenakis.model

import bundles.PublicProperty
import bundles.publicProperty

class ScoreObjectRegistry : ObjectRegistry<ScoreObject>() {
    override val objects: MutableList<ScoreObject> = mutableListOf()

    override val objectType: String
        get() = "Score object"

    fun availableName(prefix: String): String {
        for (n in 1..Int.MAX_VALUE) {
            val name = "${prefix}$n"
            if (!has(name)) return name
        }
        throw AssertionError()
    }

    fun nameForCopy(obj: ScoreObject): String = availableName("${obj.name}_copy")

    fun nameForClone(obj: ScoreObject): String = availableName("${obj.name}_clone")

    companion object : PublicProperty<ScoreObjectRegistry> by publicProperty("ScoreObjectRegistry")
}