package xenakis.model.player

import bundles.publicProperty
import hextant.context.Context
import javafx.scene.layout.Pane
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.*
import xenakis.model.Settings
import xenakis.model.flow.NodeTree
import xenakis.model.flow.SynthObjectNode
import xenakis.model.player.ScoreEventCollector.Event
import xenakis.model.score.*
import xenakis.sc.client.SuperColliderClient
import xenakis.ui.misc.PlayHead
import xenakis.ui.score.ScoreObjectGroupView
import xenakis.ui.score.ScoreObjectView
import xenakis.ui.score.ScorePane
import java.lang.ref.WeakReference
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

class ScorePlayer private constructor(
    val id: Int, val context: Context,
) : AbstractPlayer(DELTA_T, context[Settings].lookAhead) {
    private var loopedTime: Decimal = zero

    private var isAttached = false
    private lateinit var rootScore: Score
    lateinit var events: ScoreEventCollector
        private set

    public override val playHead: PlayHead = PlayHead(context)

    override val client: SuperColliderClient = context[SuperColliderClient]
    private val activeObjects = context[ActiveObjectsManager]
    private val nodeTree = context[NodeTree]

    private var lastPlayFrom: Decimal = PlayHead.START

    val loopingActivated = reactiveVariable(false)

    override val loop: Boolean
        get() = loopingActivated.now

    override val maxTime: Decimal
        get() = rootScore.maxTime.now

    private fun detach() {
        if (!isAttached) return
        events.removeListeners()
        isAttached = false
    }

    private fun attachTo(score: Score) {
        rootScore = score
        events = ScoreEventCollector(score, context[Settings])
        events.player = this
        isAttached = true
    }

    fun isAttachedTo(target: Pane) = playHead.pane == target

    fun attachToScoreView(pane: ScorePane) {
        detach()
        playHead.attachTo(pane, verticalPadding = 20.0)
        attachTo(pane.score)
    }

    fun attachToView(view: ScoreObjectView) {
        detach()
        val score = if (view is ScoreObjectGroupView) view.obj.score else simpleScore(view.instance.obj)
        playHead.attachTo(view, verticalPadding = 0.0)
        attachTo(score)
    }

    fun movePlayHeadToStart() {
        if (!isPlaying.now) {
            playHead.movePlayHeadToStart()
        }
    }

    private fun activeObjects(time: Decimal, delta: Decimal): List<Event> {
        val dest = mutableListOf<Event>()
        collectActiveObjects(ObjectPosition(0.0, 0.0), rootScore, time, delta, dest)
        return dest
    }

    private fun collectActiveObjects(
        position: ObjectPosition, score: Score, time: Decimal, delta: Decimal,
        dest: MutableList<Event>,
    ) {
        if (position.time - delta > time) return
        for (inst in score.objectInstances) {
            val obj = inst.obj
            val absolutePosition = position + inst.position
            if (obj is ScoreObjectGroup) {
                collectActiveObjects(absolutePosition, obj.score, time, delta, dest)
            } else if (absolutePosition.time - delta <= time && time <= absolutePosition.time + obj.duration) {
                dest.add(Event(Event.Type.Dummy, absolutePosition, inst))
            }
        }
    }

    override fun startPlay(startFrom: Decimal): Boolean {
        execute {
            client.sendAsync("start_play", listOf(id))
            context[Recorder].startingPlayback()
            Logger.fine("Starting playback at $startFrom", Logger.Category.Playback)
            lastPlayFrom = startFrom
            loopedTime = zero
            val activeObjects = activeObjects(startFrom, delta = context[Settings].lookAhead)
            for ((_, position, inst) in activeObjects) {
                if (!inst.muted.now) {
                    scheduleInstantly(inst, position)
                }
            }
        }
        return true
    }

    fun scheduleInstantly(inst: ScoreObjectInstance, position: ObjectPosition) {
        val obj = inst.obj
        val delta = position.time - playHead.currentTime
        val pos = ObjectPosition(playHead.currentTime + delta.coerceAtLeast(zero), position.y)
        Logger.fine("Scheduling $obj at $pos, delta: $delta", Logger.Category.Playback)
        scheduleObject(obj, position, cutoff = -delta.coerceAtMost(zero))
    }

    override fun looped() {
        loopedTime += maxTime
        events.unscheduleAll()
    }

    override fun scheduleEvents(t: Decimal, delta: Decimal) = execute {
        for (ev in events.eventsAt(t - delta, delta * 5)) {
            val (type, position, inst) = ev
            if (inst.muted.now) continue
            val obj = inst.obj
            when (type) {
                Event.Type.ObjectStart -> {
                    Logger.fine("ObjectStart: $obj at $position", Logger.Category.Playback)
                    scheduleObject(obj, position, cutoff = zero)
                }

                Event.Type.ObjectEnd -> {
//                    Logger.fine("ObjectEnd: $obj at $position", Logger.Category.Playback)
//                    val startPos = position + ObjectPosition(-obj.duration, zero)
//                    if (obj.duration == zero) continue
//                    val suffix = activeObjects.remove(obj, startPos) ?: continue
//                    stopObject(obj, startPos, suffix)
                }

                else -> {}
            }
            ev.scheduled = true
        }
    }

    fun scheduleObject(obj: ScoreObject, absolutePosition: ObjectPosition, cutoff: Decimal) {
        try {
            if (!obj.validate()) return
        } catch (e: Exception) {
            Logger.error("Failed to validate $obj", e, Logger.Category.Playback)
            return
        }
        val time = absolutePosition.time + loopedTime - lastPlayFrom
        val timeForExecution = (time + context[Settings].scLangLatency.now).toString()
        val activeObject = try {
            activeObjects.insert(this, obj, absolutePosition)
        } catch (e: Exception) {
            Logger.error("Failed to insert $obj into active object manager", e, Logger.Category.Playback)
            return
        }
        val placement = if (obj is SynthObject) {
            try {
                val node = SynthObjectNode(obj, activeObject)
                nodeTree.addNode(node)
            } catch (e: Exception) {
                Logger.error("Failed to insert $obj into audio flow graph", e, Logger.Category.Playback)
                return
            }
        } else null
        val code = try {
            obj.writeCode(activeObject.uniqueName, placement, cutoff.takeIf { it > zero } ?: zero)
        } catch (e: Exception) {
            Logger.error("Failed to write code for $obj", e, Logger.Category.Playback)
        }
        if (code == "") return
        try {
            client.send("schedule", listOf(timeForExecution, id, code))
        } catch (e: Exception) {
            Logger.error("Failed to schedule $obj", e, Logger.Category.Playback)
        }
        Logger.fine("unique name for $obj at $time: ${activeObject.uniqueName}", Logger.Category.Playback)
        Logger.fine("time for execution: ${timeForExecution}s", Logger.Category.Playback)
    }

    fun stopPlayBackInstantly(obj: ScoreObject, pos: ObjectPosition) = execute {
        val active = context[ActiveObjectsManager].getActiveObject(obj, pos) ?: return@execute
        stopObjectInstantly(active)
    }

    private fun stopObjectInstantly(active: ActiveScoreObject): CompletableFuture<String> = when (active.obj) {
        is SynthObject -> {
            val name = active.superColliderName
            client.eval("if ($name != nil) { $name.release; } { \"'$name' not found\".postln; }")
        }

        is ProcessObject, is TaskObject -> {
            val name = active.superColliderName
            client.eval("$name.stop;")
        }

        else -> CompletableFuture.completedFuture("unknown")
    }

    override fun pausePlayback() = execute {
        Logger.info("Pausing playback", Logger.Category.Playback)
        client.sendAsync("pause_play", listOf(id))
        context[Recorder].pausingPlayback()
        val futures = mutableListOf<CompletableFuture<*>>()
        activeObjects.forEach { activeObject ->
            if (activeObject is ActiveScoreObject && activeObject.player == this) {
                futures.add(stopObjectInstantly(activeObject))
            }
        }
        CompletableFuture.allOf(*futures.toTypedArray()).join()
        activeObjects.clear(this)
        events.resetEvents()
    }

    companion object {
        private val executor = Executors.newSingleThreadExecutor()

        private val DELTA_T = 0.03.toDecimal()

        private fun simpleScore(obj: ScoreObject): Score {
            val inst = ScoreObjectInstance(obj, ObjectPosition.ZERO)
            val score = Score(mutableListOf(inst))
            score.initialize(obj.context, obj)
            return score
        }

        val CURRENT = publicProperty<ScorePlayer>("ScorePlayer")

        private var nextId = 0
        private val all = mutableListOf<WeakReference<ScorePlayer>>()

        fun all(): List<ScorePlayer> {
            all.removeIf { it.get() == null }
            return all.mapNotNull { ref -> ref.get() }
        }

        fun create(context: Context): ScorePlayer {
            val id = nextId++
            val player = ScorePlayer(id, context)
            all.add(WeakReference(player))
            return player
        }

        fun execute(action: () -> Unit) {
            executor.execute(action)
        }
    }
}