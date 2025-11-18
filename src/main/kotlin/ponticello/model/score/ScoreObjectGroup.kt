package ponticello.model.score

import hextant.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.copy
import ponticello.model.obj.BusReference
import ponticello.model.registry.ObjectReference
import ponticello.sc.editor.BusSelector
import reaktive.value.ReactiveVariable
import reaktive.value.reactiveVariable

@Serializable
@SerialName("SubScore")
class ScoreObjectGroup(
    override val score: Score,
    @SerialName("defaultBus") val defaultBusRef: ReactiveVariable<BusReference> = reactiveVariable(ObjectReference.none()),
) : AbstractScoreObjectGroup() {
    @SerialName("name")
    override var _name: ReactiveVariable<String>? = null

    @Transient
    lateinit var busSelector: BusSelector
        private set

    override fun initialize(context: Context) {
        if (initialized) return
        super.initialize(context)
        busSelector = BusSelector()
        busSelector.syncWith(defaultBusRef)
        busSelector.initialize(context)
    }

    override fun onLoadedIntoRegistry() {
        super.onLoadedIntoRegistry()
        for (obj in score.objects) {
            obj.addedToScore(score)
        }
    }

    override fun onRemoved() {
        super.onRemoved()
        for (obj in score.objects) {
            obj.removedFromScore(Score.RegistryOption.ASK_IF_NEEDED)
        }
    }

    override fun cloneWith(score: Score): AbstractScoreObjectGroup = ScoreObjectGroup(score, defaultBusRef.copy())
}
