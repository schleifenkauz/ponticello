package ponticello.model.player

import bundles.publicProperty
import ponticello.impl.*
import ponticello.model.GlobalSettings
import ponticello.model.live.QuantizationConfig
import ponticello.model.registry.ClockRegistry
import ponticello.model.score.ObjectPosition
import ponticello.model.score.ScoreObjectInstance
import ponticello.sc.client.SuperColliderClient
import ponticello.ui.misc.PlayHead
import ponticello.ui.score.ScorePane
import ponticello.ui.score.SingleObjectScorePane
import reaktive.value.ReactiveBoolean
import reaktive.value.ReactiveValue
import reaktive.value.now
import reaktive.value.reactiveVariable
import java.util.concurrent.Executors

class ScorePlayer private constructor(
    val id: Int, val pane: ScorePane,
    val scheduler: ScoreObjectScheduler,
    private val loopingActivated: ReactiveBoolean,
) {
    private val playing = reactiveVariable(false)
    private val scheduled = reactiveVariable(false)
    var loopedTime: Decimal = zero
        private set
    var lastPlayFrom: Decimal = zero
        private set

    val isPlaying: ReactiveValue<Boolean> = playing
    val isScheduled: ReactiveValue<Boolean> = scheduled

    val context get() = pane.context

    private val lookAhead get() = context[GlobalSettings].lookAhead

    private val client: SuperColliderClient = context[SuperColliderClient]
    private val activeObjects = context[ActiveObjectsManager]
    val events: ScoreEventCollector = ScoreEventCollector(pane.score, pane.context[GlobalSettings], this)
    val playHead: PlayHead = PlayHead(pane)

    private var currentClock: ClockObject? = null

    val currentTime get() = if (isScheduled.now) playHead.currentTime + lookAhead else playHead.currentTime

    val timeOffset: Decimal get() = loopedTime - lastPlayFrom

    private val maxTime: Decimal
        get() = pane.score.maxTime.now

    fun doCycle(clock: ClockObject, time: Decimal) = execute {
        var scoreTime = time - loopedTime

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
                events.resetEvents()
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
        currentClock = clock
        clock.scheduleStart(this, quantization)
    }

    private fun getQuantization(): QuantizationConfig? {
        val rootObj = (pane as? SingleObjectScorePane)?.rootObj
        return rootObj?.quantizationConfig
    }

    fun getClock(): ClockObject = currentClock
        ?: getQuantization()?.clock?.now?.get()
        ?: context[ClockRegistry].getDefault()

    fun startPlaying() = execute {
        playing.set(true)
        System.err.println("Start Player [$id]")
        client.send("start_play", listOf(id))
        context[Recorder].startingPlayback()
        val time = playHead.currentTime
        Logger.fine("Starting playback at $time", Logger.Category.Playback)
        lastPlayFrom = time
        loopedTime = zero
        val activeObjects = scheduler.activeObjects(time, context[GlobalSettings].lookAhead, pane.score)
        for ((_, position, inst) in activeObjects) {
            scheduleInstantly(inst, position)
        }
    }

    fun pause() {
        if (!isScheduled.now && !isPlaying.now) return
        scheduled.set(false)
        playing.set(false)
        loopedTime = zero
        lastPlayFrom = zero
        getClock().stop(this)
        currentClock = null
        client.send("pause_play", listOf(id))
        freeActiveObjects()
        events.recomputeEvents()
    }

    private fun freeActiveObjects() = execute {
        Logger.info("Pausing playback", Logger.Category.Playback)
        context[Recorder].pausingPlayback()
        for (activeObject in activeObjects.all()) {
            if (activeObject.player == this) {
                println("Stopping $activeObject")
                scheduler.stopObjectInstantly(activeObject)
            }
        }
        activeObjects.clear(this)
    }

    //Only inside ScorePlayer.execute
    fun scheduleInstantly(inst: ScoreObjectInstance, position: ObjectPosition) {
        val obj = inst.obj
        val delta = position.time - playHead.currentTime
        val cutoff = (-delta).coerceAtLeast(zero)
        if (cutoff >= inst.obj.duration) return
        val pos = ObjectPosition(position.time, position.y)
        Logger.fine("Scheduling $obj at $pos, delta: $delta", Logger.Category.Playback)
        scheduler.scheduleObject(obj, pos, cutoff = cutoff, this)
    }


    companion object {
        val CURRENT = publicProperty<ScorePlayer>("ScorePlayer")

        val MAIN = publicProperty<ScorePlayer>("MainScorePlayer")

        private val executor = Executors.newSingleThreadExecutor()

        fun execute(action: () -> Unit) {
            executor.execute(action)
        }

        private val all = mutableListOf<ScorePlayer>()

        fun clearInstances() {
            all.clear()
        }

        fun instances(): List<ScorePlayer> = all

        fun create(pane: ScorePane, loopingActivated: ReactiveBoolean): ScorePlayer {
            val id = all.size
            val scheduler = pane.context[ScoreObjectScheduler]
            val player = ScorePlayer(id, pane, scheduler, loopingActivated)
            all.add(player)
            return player
        }

        fun getById(id: Int): ScorePlayer = all[id]
    }
}