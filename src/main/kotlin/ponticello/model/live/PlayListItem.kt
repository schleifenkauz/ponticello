package ponticello.model.live

import hextant.context.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.Decimal
import ponticello.model.obj.AbstractContextualObject
import ponticello.model.obj.ScoreObjectReference
import ponticello.model.player.ScorePlayer
import ponticello.model.registry.NamedObject
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.score.ScoreObject
import ponticello.model.score.UnresolvedScoreObject
import reaktive.value.ReactiveBoolean
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable

@Serializable
class PlayListItem(
    private val objReference: ScoreObjectReference,
    private val loopingActivated: ReactiveVariable<Boolean>,
    private val absoluteScoreY: ReactiveVariable<Decimal>,
) : AbstractContextualObject(), NamedObject {
    @Transient
    lateinit var obj: ScoreObject
        private set
    @Transient
    lateinit var player: ScorePlayer
        private set

    override fun initialize(context: Context) {
        super.initialize(context)
        obj = objReference.resolve(context[ScoreObjectRegistry]) ?: UnresolvedScoreObject()
        player = ScorePlayer.create()
    }

    override val isAdded: ReactiveBoolean
        get() = obj.isAdded
    override val name: ReactiveValue<String>
        get() = obj.name
}