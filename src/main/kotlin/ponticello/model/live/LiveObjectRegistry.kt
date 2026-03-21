package ponticello.model.live

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import com.illposed.osc.OSCMessage
import hextant.context.Context
import kotlinx.serialization.Serializable
import ponticello.impl.Logger
import ponticello.model.registry.IdProvider
import ponticello.model.registry.ObjectListSerializer
import ponticello.model.registry.ObjectRegistry
import ponticello.model.score.ScoreObject
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.getArgument

@Serializable(with = LiveObjectRegistry.Serializer::class)
class LiveObjectRegistry(
    override val objects: MutableList<LiveObject>,
) : ObjectRegistry<LiveObject>() {
    override val objectType: String
        get() = "Live Object"

    val ids = IdProvider(this)

    override fun initialize(context: Context) {
        context[LiveObjectRegistry] = this
        super.initialize(context)
        val client = context[SuperColliderClient]
        client.addListener("/start_live_obj") { _, msg -> getLiveObjectById(msg)?.play() }
        client.addListener("/pause_live_obj") { _, msg -> getLiveObjectById(msg)?.pause() }
        client.addListener("/toggle_live_obj") { _, msg -> getLiveObjectById(msg)?.toggle() }
    }

    private fun getLiveObjectById(msg: OSCMessage): LiveObject? {
        val objId = msg.getArgument<Int>(0, "Object ID") ?: return null
        val obj = ids.getById(objId)
        if (obj == null) {
            Logger.warn("Received /start_live_obj for unknown object ID: $objId", Logger.Category.OSC)
        }
        return obj
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