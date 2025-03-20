package xenakis.model.registry

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import kotlinx.serialization.Serializable
import reaktive.value.now
import xenakis.model.score.ScoreObject

@Serializable
class ScoreObjectRegistry(override val objects: MutableList<ScoreObject>) : ObjectRegistry<ScoreObject>() {
    override val objectType: String
        get() = "score_object"

    override fun initialize(context: Context) {
        context[ScoreObjectRegistry] = this
        super.initialize(context)
    }

    fun availableName(prefix: String): String {
        for (n in 1..Int.MAX_VALUE) {
            val name = "${prefix}_$n"
            if (!has(name)) return name
        }
        throw AssertionError()
    }

    fun nameForClone(obj: ScoreObject): String {
        val name = obj.name.now
        val prefix = name.dropLastWhile { it.isDigit() }.removeSuffix("_")
        return availableName(prefix)
    }

    companion object : PublicProperty<ScoreObjectRegistry> by publicProperty("ScoreObjectRegistry") {
        fun createDefault() = ScoreObjectRegistry(mutableListOf())
    }
}