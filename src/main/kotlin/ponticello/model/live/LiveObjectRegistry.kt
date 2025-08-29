package ponticello.model.live

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import kotlinx.serialization.Serializable
import ponticello.model.registry.ObjectListSerializer
import ponticello.model.registry.ObjectRegistry
import ponticello.model.score.ScoreObject

@Serializable(with = LiveObjectRegistry.Serializer::class)
class LiveObjectRegistry(
    override val objects: MutableList<LiveObject>,
) : ObjectRegistry<LiveObject>() {
    override val objectType: String
        get() = "Live Object"

    override fun initialize(context: Context) {
        context[LiveObjectRegistry] = this
        super.initialize(context)
    }

    fun getLiveScoreObject(obj: ScoreObject): LiveScoreObject? =
        objects.find { it is LiveScoreObject && it.scoreObject == obj } as? LiveScoreObject

    fun getOrCreateLiveScoreObject(obj: ScoreObject): LiveScoreObject {
        val existing = getLiveScoreObject(obj)
        if (existing != null) return existing
        val new = LiveScoreObject.create(obj)
        return new
    }

    object Serializer : ObjectListSerializer<LiveObject, LiveObjectRegistry>(
        LiveObject.serializer(), ::LiveObjectRegistry
    )

    companion object : PublicProperty<LiveObjectRegistry> by publicProperty("LIVE_TASK_REGISTRY") {
        fun createDefault(): LiveObjectRegistry = LiveObjectRegistry(mutableListOf())
    }
}