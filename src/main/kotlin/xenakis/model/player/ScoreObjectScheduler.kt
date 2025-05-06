package xenakis.model.player

import reaktive.value.now
import xenakis.impl.Decimal
import xenakis.impl.Logger
import xenakis.impl.unaryMinus
import xenakis.impl.zero
import xenakis.model.Settings
import xenakis.model.flow.NodeTree
import xenakis.model.flow.SynthObjectNode
import xenakis.model.player.ScoreEventCollector.Event
import xenakis.model.player.ScorePlayer.Companion.execute
import xenakis.model.score.*
import xenakis.sc.client.SuperColliderClient
import java.util.concurrent.CompletableFuture

class ScoreObjectScheduler(val player: ScorePlayer) {
    private val client = player.context[SuperColliderClient]
    private val context = player.context
    private val nodeTree = context[NodeTree]
    private val activeObjects = context[ActiveObjectsManager]

    fun scheduleEvents(events: List<Event>) = execute {
        for (ev in events) {
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
                    val active = activeObjects.remove(obj, startPos) ?: continue
                    active.stillActive = false
                    if (obj is TempoGridObject && obj.meter.isResolved.now) {
                        val meter = obj.meter.force()
                        meter.clock.detach(player)
                    }
                }

                else -> {}
            }
            ev.scheduled = true
        }
    }

    fun stopObjectInstantly(active: ActiveScoreObject): CompletableFuture<String> {
        if (!active.stillActive) return CompletableFuture.completedFuture("")
        active.stillActive = false
        return when (active.obj) {
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
    }

    fun scheduleObject(obj: ScoreObject, absolutePosition: ObjectPosition, cutoff: Decimal): ActiveScoreObject? {
        try {
            if (!obj.validate()) return null
        } catch (e: Exception) {
            Logger.error("Failed to validate $obj", e, Logger.Category.Playback)
            return null
        }
        val time = absolutePosition.time + player.loopOffset
        val timeForExecution = (time + context[Settings].scLangLatency.now).toString()
        if (obj is TempoGridObject && obj.meter.isResolved.now) {
            val meter = obj.meter.force()
            meter.clock.attach(player, offset = zero)
        }
        val activeObject = try {
            activeObjects.insert(player, obj, absolutePosition)
        } catch (e: Exception) {
            Logger.error("Failed to insert $obj into active object manager", e, Logger.Category.Playback)
            return null
        }
        val placement = if (obj is SynthObject) {
            try {
                val node = SynthObjectNode(obj, activeObject)
                nodeTree.addNode(node)
            } catch (e: Exception) {
                Logger.error("Failed to insert $obj into audio flow graph", e, Logger.Category.Playback)
                return null
            }
        } else null
        val code = try {
            obj.writeCode(activeObject.uniqueName, placement, cutoff.takeIf { it > zero } ?: zero)
        } catch (e: Exception) {
            Logger.error("Failed to write code for $obj", e, Logger.Category.Playback)
        }
        if (code == "") return null
        try {
            client.send("schedule", listOf(timeForExecution, player.id, code))
        } catch (e: Exception) {
            Logger.error("Failed to schedule $obj", e, Logger.Category.Playback)
        }
        Logger.fine("unique name for $obj at $time: ${activeObject.uniqueName}", Logger.Category.Playback)
        Logger.fine("time for execution: ${timeForExecution}s", Logger.Category.Playback)
        return activeObject
    }

    fun activeObjects(time: Decimal, delta: Decimal): List<Event> {
        val dest = mutableListOf<Event>()
        collectActiveObjects(ObjectPosition(0.0, 0.0), player.pane.score, time, delta, dest)
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

}