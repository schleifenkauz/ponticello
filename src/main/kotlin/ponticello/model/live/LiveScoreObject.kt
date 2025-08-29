package ponticello.model.live

import hextant.context.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.zero
import ponticello.model.obj.AbstractContextualObject
import ponticello.model.obj.ScoreObjectReference
import ponticello.model.player.ScorePlayer
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.registry.reference
import ponticello.model.score.ObjectPosition
import ponticello.model.score.Score
import ponticello.model.score.ScoreObject
import ponticello.model.score.UnresolvedScoreObject
import ponticello.ui.launcher.PonticelloMainActivity
import ponticello.ui.misc.PlayHead
import reaktive.Observer
import reaktive.and
import reaktive.value.ReactiveBoolean
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.reactiveVariable

@Serializable
class LiveScoreObject(
    private val objReference: ScoreObjectReference,
) : LiveObject, AbstractContextualObject() {
    override val quantization: QuantizationConfig = QuantizationConfig.createDefault()
    val absoluteScoreY = reactiveVariable(zero(ObjectPosition.Y_PRECISION))
    val loopingActivated: ReactiveVariable<Boolean> = reactiveVariable(false)

    val scoreObject: ScoreObject get() = objReference.get() ?: UnresolvedScoreObject()

    @Transient
    lateinit var player: ScorePlayer
        private set

    @Transient
    var playHead: PlayHead? = null

    override val name: ReactiveValue<String>
        get() = scoreObject.name
    override val isAdded: ReactiveBoolean
        get() = scoreObject.isAdded

    @Transient
    private var scheduled = reactiveVariable(false)
    @Transient
    private var playing = reactiveVariable(false)
    @Transient
    private lateinit var playerObserver: Observer

    override val isScheduled: ReactiveValue<Boolean> get() = scheduled
    override val isPlaying: ReactiveBoolean get() = playing

    override fun initialize(context: Context) {
        super.initialize(context)
        objReference.resolve(context[ScoreObjectRegistry]) ?: UnresolvedScoreObject()
        quantization.initialize(context)
        val score = Score.makeScore(scoreObject)
        playHead = playHead ?: PlayHead()
        player = ScorePlayer.create(score, playHead!!, loopingActivated, quantization)
        playerObserver = scheduled.bind(player.isScheduled) and playing.bind(player.isPlaying)
    }

    override fun play() {
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun reset() {
        player.pause()
        player.playHead.movePlayHeadToStart()
    }

    override fun onRemoved() {
        reset()
    }

    fun inferQuantizationFrom(absolutePosition: ObjectPosition, context: Context): Boolean {
        val mainScoreView = context[PonticelloMainActivity].mainScoreView
        val (gridStart, meter) = mainScoreView.getNearestGrid(absolutePosition) ?: return false
        quantization.meter.set(meter.reference())
        var delta = absolutePosition.time - gridStart
        while (delta < zero) delta += scoreObject.duration
        while (delta >= scoreObject.duration) delta -= scoreObject.duration
        val (offsetUnit, offsetValue) = meter.represent(delta)
        quantization.offsetUnit.set(offsetUnit)
        quantization.offsetValue.set(offsetValue)
        quantization.enableQuantization.set(true)
        return true
    }

    companion object {
        fun create(obj: ScoreObject): LiveScoreObject = LiveScoreObject(obj.reference())
    }
}