package xenakis.model.player

import bundles.publicProperty
import reaktive.value.ReactiveBoolean
import reaktive.value.now
import xenakis.impl.*
import xenakis.model.Settings
import xenakis.model.flow.NodeTree
import xenakis.model.flow.SynthObjectNode
import xenakis.model.player.ScoreEventCollector.Event
import xenakis.model.score.*
import xenakis.sc.client.SuperColliderClient
import xenakis.ui.launcher.XenakisMainActivity
import xenakis.ui.misc.PlayHead
import xenakis.ui.score.ScorePane
import java.lang.ref.WeakReference
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

class ScorePlayer private constructor(
    val id: Int, val pane: ScorePane,
    private val loopingActivated: ReactiveBoolean
) : AbstractPlayer(DELTA_T, pane.context[Settings].lookAhead) {
    private var loopedTime: Decimal = zero
    private var lastPlayFrom: Decimal = PlayHead.START

    val context get() = pane.context

    override val client: SuperColliderClient = context[SuperColliderClient]
    private val activeObjects = context[ActiveObjectsManager]
    private val nodeTree = context[NodeTree]

    private val events: ScoreEventCollector = ScoreEventCollector(pane.score, pane.context[Settings])

    public override val playHead: PlayHead = PlayHead(pane)

    override val loop: Boolean
        get() = loopingActivated.now

    override val maxTime: Decimal
        get() = pane.score.maxTime.now

    fun isMainScorePlayer() = pane == context[XenakisMainActivity].mainScoreView

    fun movePlayHeadToStart() {
        if (!isPlaying.now) {
            playHead.movePlayHeadToStart()
        }
    }

    private fun activeObjects(time: Decimal, delta: Decimal): List<Event> {
        val dest = mutableListOf<Event>()
        collectActiveObjects(ObjectPosition(0.0, 0.0), pane.score, time, delta, dest)
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
                    Logger.fine("ObjectEnd: $obj at $position", Logger.Category.Playback)
                    val startPos = position + ObjectPosition(-obj.duration, zero)
                    if (obj.duration == zero) continue
                    activeObjects.remove(obj, startPos) ?: continue
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

        val CURRENT = publicProperty<ScorePlayer>("ScorePlayer")

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

        fun execute(action: () -> Unit) {
            executor.execute(action)
        }
    }
}