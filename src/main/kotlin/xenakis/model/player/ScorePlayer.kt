package xenakis.model.player

import bundles.publicProperty
import reaktive.value.ReactiveBoolean
import reaktive.value.ReactiveValue
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.*
import xenakis.model.Settings
import xenakis.model.live.QuantizationConfig
import xenakis.model.registry.ClockRegistry
import xenakis.model.score.ObjectPosition
import xenakis.model.score.ScoreObjectInstance
import xenakis.sc.client.SuperColliderClient
import xenakis.ui.misc.PlayHead
import xenakis.ui.score.ScorePane
import xenakis.ui.score.SingleObjectScorePane
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

class ScorePlayer private constructor(
    val id: Int, val pane: ScorePane,
    val scheduler: ScoreObjectScheduler,
    private val loopingActivated: ReactiveBoolean,
) {
    private val playing = reactiveVariable(false)
    private val scheduled = reactiveVariable(false)
    private var loopedTime: Decimal = zero
    private var lastPlayFrom: Decimal = zero

    val isPlaying: ReactiveValue<Boolean> = playing
    val isScheduled: ReactiveValue<Boolean> = scheduled

    val context get() = pane.context

    private val lookAhead get() = context[Settings].lookAhead

    private val client: SuperColliderClient = context[SuperColliderClient]
    private val activeObjects = context[ActiveObjectsManager]
    private val events: ScoreEventCollector = ScoreEventCollector(pane.score, pane.context[Settings])
    val playHead: PlayHead = PlayHead(pane)

    val currentTime get() = if (isScheduled.now) playHead.currentTime + lookAhead else playHead.currentTime

    val loopOffset: Decimal get() = loopedTime - lastPlayFrom

    private val maxTime: Decimal
        get() = pane.score.maxTime.now

    fun doCycle(clock: ClockObject, elapsedTime: Decimal) = execute {
        var scoreTime = elapsedTime - loopedTime

        var playHeadPos = scoreTime - lookAhead
        if (playHeadPos < zero) playHeadPos += maxTime
        if (playHeadPos >= maxTime) {
            if (loopingActivated.now) {
                playHeadPos -= maxTime
            } else {
                playHead.movePlayHeadToStart()
                pause()
                return@execute
            }
        }
        playHead.movePlayHead(playHeadPos)

        if (scoreTime >= maxTime) {
            if (!loopingActivated.now) {
                return@execute
            } else {
                loopedTime += maxTime
                scoreTime -= maxTime
                events.rescheduleAll()
            }
        }

        val events = events.eventsAt(scoreTime - clock.period, delta = clock.period * 5)
        scheduler.scheduleEvents(events, this)
    }

    fun play() {
        if (isScheduled.now) return
        scheduled.set(true)
        val quantization = getQuantization()
        val clock = getClock()
        clock.scheduleStart(this, quantization)
    }

    private fun getQuantization(): QuantizationConfig? {
        val rootObj = (pane as? SingleObjectScorePane)?.rootObj
        return rootObj?.quantizationConfig
    }

    fun getClock(): ClockObject =
        getQuantization()?.clock?.now?.get() ?: context[ClockRegistry].getDefault()

    fun startPlaying() = execute {
        playing.set(true)
        client.sendAsync("start_play", listOf(id))
        context[Recorder].startingPlayback()
        val time = playHead.currentTime
        Logger.fine("Starting playback at $time", Logger.Category.Playback)
        lastPlayFrom = time
        loopedTime = zero
        val activeObjects = scheduler.activeObjects(time, context[Settings].lookAhead, pane.score)
        for ((_, position, inst) in activeObjects) {
            if (!inst.muted.now) {
                scheduleInstantly(inst, position)
            }
        }
    }

    fun pause() {
        if (!isScheduled.now && !isPlaying.now) return
        scheduled.set(false)
        playing.set(false)
        loopedTime = zero
        lastPlayFrom = zero
        getClock().stop(this)
        freeActiveObjects()
    }

    //Only inside ScorePlayer.execute
    fun scheduleInstantly(inst: ScoreObjectInstance, position: ObjectPosition) {
        val obj = inst.obj
        val delta = position.time - playHead.currentTime
        val pos = ObjectPosition(playHead.currentTime + delta.coerceAtLeast(zero), position.y)
        Logger.fine("Scheduling $obj at $pos, delta: $delta", Logger.Category.Playback)
        scheduler.scheduleObject(obj, position, cutoff = -delta.coerceAtMost(zero), this)
    }

    private fun freeActiveObjects() = execute {
        Logger.info("Pausing playback", Logger.Category.Playback)
        client.sendAsync("pause_play", listOf(id))
        context[Recorder].pausingPlayback()
        for (activeObject in activeObjects.all()) {
            if (activeObject.player == this) {
                println("Stopping $activeObject")
                scheduler.stopObjectInstantly(activeObject)
            }
        }
        activeObjects.clear(this)
        events.resetEvents()
    }

    companion object {
        val CURRENT = publicProperty<ScorePlayer>("ScorePlayer")

        val MAIN = publicProperty<ScorePlayer>("MainScorePlayer")

        private val executor = Executors.newSingleThreadExecutor()

        fun execute(action: () -> Unit) {
            executor.execute(action)
        }

        private var nextId = 0

        private val all = mutableListOf<WeakReference<ScorePlayer>>()

        fun clearInstances() {
            all.clear()
        }

        fun instances(): List<ScorePlayer> {
            all.removeIf { it.get() == null }
            return all.mapNotNull { ref -> ref.get() }
        }

        fun create(pane: ScorePane, loopingActivated: ReactiveBoolean): ScorePlayer {
            val id = nextId++
            val scheduler = pane.context[ScoreObjectScheduler]
            val player = ScorePlayer(id, pane, scheduler, loopingActivated)
            all.add(WeakReference(player))
            return player
        }
    }
}