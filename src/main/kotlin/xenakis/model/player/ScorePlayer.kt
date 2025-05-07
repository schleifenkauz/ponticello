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
import xenakis.model.score.ScoreObject
import xenakis.model.score.ScoreObjectInstance
import xenakis.sc.client.SuperColliderClient
import xenakis.ui.misc.PlayHead
import xenakis.ui.score.ScorePane
import xenakis.ui.score.SingleObjectScorePane
import java.lang.ref.WeakReference
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

class ScorePlayer private constructor(
    val id: Int, val pane: ScorePane,
    val scheduler: ScoreObjectScheduler,
    private val loopingActivated: ReactiveBoolean,
) {
    private val _isPlaying = reactiveVariable(false)
    private var loopedTime: Decimal = zero
    var lastPlayFrom: Decimal = zero
        private set

    val isPlaying: ReactiveValue<Boolean> = _isPlaying

    val context get() = pane.context

    private val client: SuperColliderClient = context[SuperColliderClient]
    private val activeObjects = context[ActiveObjectsManager]
    private val events: ScoreEventCollector = ScoreEventCollector(pane.score, pane.context[Settings])
    val playHead: PlayHead = PlayHead(pane)

    val currentTime get() = playHead.currentTime

    val loopOffset: Decimal get() = loopedTime - lastPlayFrom

    private val maxTime: Decimal
        get() = pane.score.maxTime.now

    fun doCycle(clock: ClockObject, elapsedTime: Decimal) {
        var scoreTime = elapsedTime - loopedTime

        var playHeadPos = scoreTime - clock.lookAhead
        if (playHeadPos < zero) playHeadPos += maxTime
        if (playHeadPos >= maxTime) {
            if (loopingActivated.now) {
                playHeadPos -= maxTime
            } else {
                playHead.movePlayHeadToStart()
                pause()
                return
            }
        }
        playHead.movePlayHead(playHeadPos)

        if (scoreTime >= maxTime) {
            if (!loopingActivated.now) {
                return
            } else {
                loopedTime += maxTime
                scoreTime -= maxTime
                events.unscheduleAll()
            }
        }

        val events = events.eventsAt(scoreTime - clock.period * 5, delta = clock.period * 5)
        scheduler.scheduleEvents(events, this)
    }

    fun play() {
        if (isPlaying.now) return
        _isPlaying.now = true
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

    fun startPlaying() {
        client.sendAsync("start_play", listOf(id))
        context[Recorder].startingPlayback()
        Logger.fine("Starting playback at ${playHead.currentTime}", Logger.Category.Playback)
        lastPlayFrom = playHead.currentTime
        loopedTime = zero
        execute {
            val activeObjects = scheduler.activeObjects(playHead.currentTime, context[Settings].lookAhead, pane.score)
            for ((_, position, inst) in activeObjects) {
                if (!inst.muted.now) {
                    scheduleInstantly(inst, position)
                }
            }
        }
    }

    fun pause() {
        if (!isPlaying.now) return
        _isPlaying.now = false
        loopedTime = zero
        lastPlayFrom = zero
        getClock().stop(this)
        freeActiveObjects()
    }

    fun scheduleInstantly(inst: ScoreObjectInstance, position: ObjectPosition) {
        val obj = inst.obj
        val delta = position.time - playHead.currentTime
        val pos = ObjectPosition(playHead.currentTime + delta.coerceAtLeast(zero), position.y)
        Logger.fine("Scheduling $obj at $pos, delta: $delta", Logger.Category.Playback)
        scheduler.scheduleObject(obj, position, cutoff = -delta.coerceAtMost(zero), this)
    }


    fun stopPlayBackInstantly(obj: ScoreObject, pos: ObjectPosition) = execute {
        val active = context[ActiveObjectsManager].getActiveObject(obj, pos) ?: return@execute
        scheduler.stopObjectInstantly(active)
    }


    private fun freeActiveObjects() = execute {
        Logger.info("Pausing playback", Logger.Category.Playback)
        client.sendAsync("pause_play", listOf(id))
        context[Recorder].pausingPlayback()
        val futures = mutableListOf<CompletableFuture<*>>()
        println()
        for (activeObject in activeObjects.all()) {
            if (activeObject.player == this) {
                println("Stopping $activeObject")
                futures.add(scheduler.stopObjectInstantly(activeObject))
            }
        }
        CompletableFuture.allOf(*futures.toTypedArray()).join()
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

        fun all(): List<ScorePlayer> {
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