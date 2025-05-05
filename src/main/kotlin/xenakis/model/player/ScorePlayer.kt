package xenakis.model.player

import bundles.publicProperty
import reaktive.value.ReactiveBoolean
import reaktive.value.ReactiveValue
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.*
import xenakis.model.Settings
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
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class ScorePlayer private constructor(
    val id: Int, val pane: ScorePane,
    private val loopingActivated: ReactiveBoolean,
) {
    private val _isPlaying = reactiveVariable(false)
    private var loopHandle: Future<*>? = null
    private var startPlayHandle: Future<*>? = null
    private var lastPlayFrom: Decimal = PlayHead.START
    private var loopedTime: Decimal = zero

    val isPlaying: ReactiveValue<Boolean> = _isPlaying

    val context get() = pane.context

    private val lookAhead get() = context[Settings].lookAhead

    private val client: SuperColliderClient = context[SuperColliderClient]
    private val activeObjects = context[ActiveObjectsManager]
    private val events: ScoreEventCollector = ScoreEventCollector(pane.score, pane.context[Settings])
    val playHead: PlayHead = PlayHead(pane)

    val scheduler = ScoreObjectScheduler(this)

    val currentTime get() = playHead.currentTime

    private val maxTime: Decimal
        get() = pane.score.maxTime.now

    val loopOffset get() = loopedTime - lastPlayFrom

    val elapsedTime: Decimal get() = loopOffset + currentTime + lookAhead

    private fun runLoop() {
        val dt = (LOOP_PERIOD / 1000.0).asTime
        if (isPlaying.now) {
            var t = playHead.currentTime + lookAhead
            if (playHead.currentTime > maxTime) {
                playHead.movePlayHeadToStart()
                if (!loopingActivated.now) {
                    pause()
                    return
                } else {
                    loopedTime += maxTime
                    events.unscheduleAll()
                }
                t = 0.0.asTime
            } else {
                playHead.advance(dt)
            }
            scheduler.scheduleEvents(events.eventsAt(t - dt, dt * 5))
        }
    }

    fun play() {
        if (isPlaying.now) return
        _isPlaying.now = true
        val rootObj = (pane as? SingleObjectScorePane)?.rootObj
        val quantization = rootObj?.quantizationConfig?.takeIf { it.meter.now.isResolved.now }
        val quantizationDelay = if (quantization == null || !quantization.enableQuantization.now) zero else {
            val quant = quantization.computeQuant(rootObj.duration)
            if (quant == zero) zero
            else {
                val offset = quantization.computeOffset()
                val meter = quantization.meter.now.force()
                meter.clock.scheduleStart(this, quant, offset)
            }
        }
        val initialDelay: Long = toMs(quantizationDelay + lookAhead)
        startPlayHandle = executor.schedule({ startPlaying() }, toMs(quantizationDelay), TimeUnit.MILLISECONDS)
        loopHandle = looper.scheduleAtFixedRate({ runLoop() }, initialDelay, LOOP_PERIOD, TimeUnit.MILLISECONDS)
    }

    private fun startPlaying() {
        client.sendAsync("start_play", listOf(id))
        context[Recorder].startingPlayback()
        Logger.fine("Starting playback at ${playHead.currentTime}", Logger.Category.Playback)
        lastPlayFrom = playHead.currentTime
        loopedTime = zero
        execute {
            val activeObjects = scheduler.activeObjects(playHead.currentTime, delta = context[Settings].lookAhead)
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
        startPlayHandle?.cancel(true)
        startPlayHandle = null
        loopHandle?.cancel(true)
        loopHandle = null
        freeActiveObjects()
    }

    fun scheduleInstantly(inst: ScoreObjectInstance, position: ObjectPosition) {
        val obj = inst.obj
        val delta = position.time - playHead.currentTime
        val pos = ObjectPosition(playHead.currentTime + delta.coerceAtLeast(zero), position.y)
        Logger.fine("Scheduling $obj at $pos, delta: $delta", Logger.Category.Playback)
        scheduler.scheduleObject(obj, position, cutoff = -delta.coerceAtMost(zero))
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

        private const val LOOP_PERIOD = 5L

        private val executor = Executors.newSingleThreadScheduledExecutor()

        fun execute(action: () -> Unit) {
            executor.execute(action)
        }

        private val looper = Executors.newScheduledThreadPool(4)

        private var nextId = 0

        private val all = mutableListOf<WeakReference<ScorePlayer>>()

        fun all(): List<ScorePlayer> {
            all.removeIf { it.get() == null }
            return all.mapNotNull { ref -> ref.get() }
        }

        fun create(pane: ScorePane, loopingActivated: ReactiveBoolean): ScorePlayer {
            val id = nextId++
            val player = ScorePlayer(id, pane, loopingActivated)
            all.add(WeakReference(player))
            return player
        }

        private fun toMs(t: Decimal) = (t * 1000).toLong()
    }
}