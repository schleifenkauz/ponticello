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

class ScorePlayer(
    val context: Context
) : AbstractPlayer(DELTA_T, context[Settings].lookAhead) {
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
        get() = rootScore.maxTime

    private fun detach() {
        if (!isAttached) return
        events.removeListeners()
        isAttached = false
    }

    private fun attachTo(score: Score) {
        detach()
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
        client.sendAsync("start_play")
        context[Recorder].startingPlayback()
        Logger.fine("Starting playback at $startFrom", Logger.Category.Playback)
        lastPlayFrom = startFrom
        val activeObjects = activeObjects(startFrom, delta = context[Settings].lookAhead)
        for ((_, position, inst) in activeObjects) {
            if (!inst.muted.now) {
                scheduleInstantly(inst, position)
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

    fun stopPlayBackInstantly(obj: ScoreObject, pos: ObjectPosition) {
        val suffix = context[ActiveObjectsManager].remove(obj, pos) ?: return
        stopObject(obj, pos, suffix)
    }

    override fun scheduleEvents(t: Decimal, delta: Decimal) {
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
                    val suffix = activeObjects.remove(obj, startPos) ?: continue
                    stopObject(obj, startPos, suffix)
                }

                else -> {}
            }
            ev.scheduled = true
        }
    }

    private fun stopObject(obj: ScoreObject, startPos: ObjectPosition, suffix: Int) {
        when (obj) {
            is SynthObject -> {
                try {
                    val node = SynthObjectNode(obj, startPos, suffix)
                    nodeTree.removeNode(node)
                } catch (e: Exception) {
                    Logger.error("Failed to remove $obj from audio flow graph", e, Logger.Category.Playback)
                }
                val name = obj.superColliderName(suffix)
                client.run("if ($name != nil) { $name.free; } { \"'$name' not found\".postln; }")
            }

            is ProcessObject, is TaskObject -> {
                val name = obj.superColliderName(suffix)
                client.run("$name.stop;")
            }

            else -> {}
        }
    }

    fun scheduleObject(obj: ScoreObject, absolutePosition: ObjectPosition, cutoff: Decimal) {
        try {
            if (!obj.validate()) return
        } catch (e: Exception) {
            Logger.error("Failed to validate $obj", e, Logger.Category.Playback)
            return
        }
        val time = absolutePosition.time - lastPlayFrom
        val timeForExecution = (time + context[Settings].scLangLatency.now).toString()
        val suffix = try {
            activeObjects.insert(this, obj, absolutePosition)
        } catch (e: Exception) {
            Logger.error("Failed to insert $obj into active object manager", e, Logger.Category.Playback)
            return
        }
        val placement = if (obj is SynthObject) {
            try {
                val node = SynthObjectNode(obj, absolutePosition, suffix)
                nodeTree.addNode(node)
            } catch (e: Exception) {
                Logger.error("Failed to insert $obj into audio flow graph", e, Logger.Category.Playback)
                return
            }
        } else null
        val uniqueName = ActiveObjectsManager.uniqueName(obj.name.now, suffix)
        val code = try {
            obj.writeCode(uniqueName, placement, cutoff.takeIf { it > zero } ?: zero)
        } catch (e: Exception) {
            Logger.error("Failed to write code for $obj", e, Logger.Category.Playback)
        }
        if (code == "") return
        try {
            client.send("schedule", listOf(timeForExecution, code))
        } catch (e: Exception) {
            Logger.error("Failed to schedule $obj", e, Logger.Category.Playback)
        }
        Logger.fine("unique name for $obj at $time: $uniqueName", Logger.Category.Playback)
        Logger.fine("time for execution: ${timeForExecution}s", Logger.Category.Playback)

    }

    override fun pausePlayback() {
        Logger.info("Pausing playback", Logger.Category.Playback)
        activeObjects.forEach { activeObject ->
            if (activeObject is ActiveScoreObject && activeObject.player == this) {
                stopObject(activeObject.obj, activeObject.absolutePosition, activeObject.suffix)
            }
        }
        context[Recorder].pausingPlayback()
        activeObjects.clear()
        events.resetEvents()
        client.send("pause_play")
    }

    override fun resetPlayback() {
        if (context[Recorder].isActive.now) context[Recorder].stopRecording()
        pausePlayback()
        client.run("s.freeAll")
    }

    companion object {
        private val DELTA_T = 0.03.toDecimal()

        private fun simpleScore(obj: ScoreObject): Score {
            val inst = ScoreObjectInstance(obj, ObjectPosition.ZERO)
            val score = Score(mutableListOf(inst))
            score.initialize(obj.context, obj)
            return score
        }

        val CURRENT = publicProperty<ScorePlayer>("MainScorePlayer")
    }
}