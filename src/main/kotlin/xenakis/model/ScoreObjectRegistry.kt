package xenakis.model

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import kotlinx.serialization.Serializable
import reaktive.value.now

@Serializable
class ScoreObjectRegistry(override val objects: MutableList<ScoreObject>) : ObjectRegistry<ScoreObject>() {
    override val objectType: String
        get() = "score-object"

    override fun getDefault(): ScoreObject = throw UnsupportedOperationException("No default score object available")

    override fun initialize(context: Context) {
        super.initialize(context)
        context[ScoreObjectRegistry] = this
    }

    fun availableName(prefix: String): String {
        for (n in 1..Int.MAX_VALUE) {
            val name = "${prefix}$n"
            if (!has(name)) return name
        }
        throw AssertionError()
    }

    fun nameForClone(obj: ScoreObject): String {
        val name = obj.name.now
        val prefix = name.dropLastWhile { it.isDigit() }
        return availableName(prefix)
    }

    companion object : PublicProperty<ScoreObjectRegistry> by publicProperty("ScoreObjectRegistry")
}