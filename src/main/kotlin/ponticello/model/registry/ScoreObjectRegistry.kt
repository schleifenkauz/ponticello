package ponticello.model.registry

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import kotlinx.serialization.Serializable
import ponticello.impl.Logger
import ponticello.model.score.ScoreObject
import ponticello.model.score.ScoreObject.Unresolved
import reaktive.value.now

@Serializable
class ScoreObjectRegistry(
    override val objects: MutableList<ScoreObject> = mutableListOf(),
) : ObjectRegistry<ScoreObject>() {
    override val objectType: String
        get() = "Score Object"

    override fun initialize(context: Context) {
        context[ScoreObjectRegistry] = this
        super.initialize(context)
    }

    override fun add(obj: ScoreObject, idx: Int) {
        if (obj is Unresolved) {
            Logger.error("Attempted to add unresolved object to ScoreObjectRegistry")
            return
        }
        super.add(obj, idx)
    }

    fun nameForClone(obj: ScoreObject): String {
        val name = obj.name.now
        val prefix = name.dropLastWhile { it.isDigit() }.removeSuffix("_")
        return availableName(prefix)
    }

    override fun syncAll() {

    }

    companion object : PublicProperty<ScoreObjectRegistry> by publicProperty("ScoreObjectRegistry") {
        fun createDefault() = ScoreObjectRegistry()
    }
}