package ponticello.model.player

import bundles.publicProperty
import javafx.application.Platform
import ponticello.impl.*
import ponticello.model.flow.AudioFlows
import ponticello.model.live.QuantizationConfig
import ponticello.model.obj.playbackSettings
import ponticello.model.obj.project
import ponticello.model.project.PLAYBACK_SETTINGS
import ponticello.model.project.get
import ponticello.model.score.*
import ponticello.sc.client.SuperColliderClient
import ponticello.ui.misc.PlayHead
import reaktive.value.ReactiveBoolean
import reaktive.value.ReactiveValue
import reaktive.value.now
import reaktive.value.reactiveVariable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

class ScorePlayer private constructor(
    val id: Int, val score: Score,
    val scheduler: ScoreObjectScheduler,
    val quantization: QuantizationConfig?, private val loopingActivated: ReactiveBoolean,
) {
    private val playing = reactiveVariable(false)
    private val scheduled = reactiveVariable(false)
    var loopedTime: Decimal = zero
        private set
    var lastPlayFrom: Decimal = zero
        private set

    lateinit var playHead: PlayHead
        private set

    val isPlaying: ReactiveValue<Boolean> = playing
    val isScheduled: ReactiveValue<Boolean> = scheduled

    val context get() = score.context

    private var scTime: Float? = null
    private val zeroLookAhead: Boolean get() = scTime != null

    val lookAhead get() = if (zeroLookAhead) zero else context.playbackSettings.lookAhead

    private val client: SuperColliderClient = context[SuperColliderClient]
    private val updater = LiveScoreUpdater(score, this)

    private var currentClock: ClockObject? = null

    val currentTime get() = if (isScheduled.now) playHead.currentTime + lookAhead else playHead.currentTime

    val timeOffset: Decimal get() = loopedTime - lastPlayFrom

    private val maxTime: Decimal
        get() = score.maxTime

    init {
        score.addListener(updater)
    }

    fun connectPlayHead(playHead: PlayHead) {
        this.playHead = playHead
        playHead.player = this
    }

    fun doCycle(time: Decimal, delta: Decimal) = execute {
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
        if (context.project[PLAYBACK_SETTINGS].scrollWithPlayHead.now) {
            Platform.runLater {
                playHead.centerInScorePane()
            }
        }
        playHead.currentTime = playHeadPos

        if (scoreTime >= maxTime) {
            if (!loopingActivated.now) {
                return@execute
            } else {
                loopedTime += maxTime
                scoreTime -= maxTime
            }
        }

        val events = mutableListOf<ScoreEvent>()
        val rangeEnd = (scoreTime + delta).coerceAtMost(maxTime)
        val timeRange = scoreTime..rangeEnd
        events.collectEvents(score, timeRange, withCutoff = false)

        if (scoreTime + delta > maxTime && loopingActivated.now) {
            val extraRange = zero..(scoreTime + delta - maxTime)
            events.collectEvents(score, extraRange, withCutoff = false, timeOffset = maxTime)
        }

        scheduler.scheduleEvents(events, this)
    }

    private fun MutableList<ScoreEvent>.collectEvents(
        score: Score, timeRange: DecimalRange, withCutoff: Boolean,
        timeOffset: Decimal = zero, position: ObjectPosition = ObjectPosition.ZERO,
    ) {
        for (inst in score.activeInstances(timeRange - position.time)) {
            if (inst.muted.now) continue
            val obj = inst.obj
            if (obj is AbstractScoreObjectGroup) {
                collectEvents(obj.score, timeRange, withCutoff, timeOffset, position + inst.position)
            } else if (obj is SoundProcess && obj.generatedScore != null && obj.usesGeneratedScore.now) {
                collectEvents(obj.generatedScore!!, timeRange, withCutoff, timeOffset, position + inst.position)
            } else {
                val start = inst.start + position.time
                val end = inst.end + position.time
                val y = if (score.parentObject is MidiObject) position.y else position.y + inst.y
                if (withCutoff) {
                    val absolutePosition = ObjectPosition(start + timeOffset, y)
                    add(ScoreEvent(ScoreEvent.Type.ObjectStart, absolutePosition, inst))
                    continue
                }
                if (start in timeRange && start != timeRange.endInclusive) {
                    val absolutePosition = ObjectPosition(start + timeOffset, y)
                    add(ScoreEvent(ScoreEvent.Type.ObjectStart, absolutePosition, inst))
                }
                if (end in timeRange && end != timeRange.endInclusive) {
                    val absolutePosition = ObjectPosition(end + timeOffset, y)
                    add(ScoreEvent(ScoreEvent.Type.ObjectEnd, absolutePosition, inst))
                }
            }
        }
    }

    fun play(scTime: Float? = null) {
        this.scTime = scTime
        if (isScheduled.now) return
        scheduled.set(true)
        val clock = getClock()
        currentClock = clock
        val quant = quantization.takeUnless { scTime != null }
        clock.scheduleStart(this, quant)
    }

    fun getClock(): ClockObject = currentClock
        ?: quantization?.clock?.now?.get()
        ?: context[ClockRegistry].getDefault()

    fun startPlaying() = execute {
        playing.set(true)
        System.err.println("Start Player [$id]")
        val time = playHead.currentTime
        client.sendAsync("/start_play", listOf(id, time.toString(), scTime))
        context[Recorder].startingPlayback()
        Logger.fine("Starting playback at $time", Logger.Category.Playback)
        lastPlayFrom = time
        loopedTime = zero
        if (!zeroLookAhead) {
            val timeRange = time..time + lookAhead
            val events = mutableListOf<ScoreEvent>()
            events.collectEvents(score, timeRange, withCutoff = true)
            events.sortBy { ev -> ev.absolutePosition.y }
            for ((type, position, inst) in events) {
                if (type == ScoreEvent.Type.ObjectStart) {
                    scheduleInstantly(inst, position)?.join()
                }
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
        currentClock = null
        execute {
            client.sendAsync("/pause_play", listOf(id))
            Logger.info("Pausing playback", Logger.Category.Playback)
            for (track in context[AudioFlows].allMidiTracks()) {
                track.allNotesOff(id)
            }
        }
    }

    fun togglePlaying() {
        if (isPlaying.now || isScheduled.now) {
            pause()
        } else {
            play()
        }
    }

    private fun freeActiveObjects() = execute {
        Logger.info("Pausing playback", Logger.Category.Playback)
        client.run("SoundProcess.stopAllProcesses(player_id:$id)")
        for (track in context[AudioFlows].allMidiTracks()) {
            track.allNotesOff(id)
        }
    }

    //Only inside ScorePlayer.execute
    fun scheduleInstantly(inst: ScoreObjectInstance, position: ObjectPosition): CompletableFuture<Int?>? {
        val obj = inst.obj
        val delta = position.time - playHead.currentTime
        val cutoff = (-delta).coerceAtLeast(zero)
        if (cutoff >= inst.obj.duration) return null
        Logger.fine("Scheduling $obj at $position, delta: $delta", Logger.Category.Playback)
        val info = ObjectPlaybackInfo(position, this, cutoff = cutoff, instance = inst)
        return scheduler.scheduleObject(obj, info)
    }

    override fun toString(): String = "ScorePlayer [$id]"

    companion object {
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

        fun create(score: Score, loopingActivated: ReactiveBoolean, quantization: QuantizationConfig?): ScorePlayer {
            val id = all.size
            val scheduler = score.context[ScoreObjectScheduler]
            val player = ScorePlayer(id, score, scheduler, quantization, loopingActivated)
            all.add(player)
            return player
        }

        fun getById(id: Int): ScorePlayer = all[id]
    }
}