package ponticello.model.player

import bundles.PublicProperty
import bundles.publicProperty
import hextant.context.Context
import ponticello.impl.Decimal
import ponticello.impl.Logger
import ponticello.impl.unaryMinus
import ponticello.impl.zero
import ponticello.model.Settings
import ponticello.model.flow.NodeTree
import ponticello.model.flow.SynthObjectNode
import ponticello.model.obj.ParameterDefObject
import ponticello.model.obj.ProcessDefObject
import ponticello.model.obj.SynthDefObject
import ponticello.model.player.ScoreEventCollector.Event
import ponticello.model.score.*
import ponticello.model.score.controls.ParameterControl
import ponticello.sc.client.SuperColliderClient
import reaktive.value.now

class ScoreObjectScheduler(val context: Context) {
    private val client = context[SuperColliderClient]
    private val nodeTree = context[NodeTree]
    private val activeObjects = context[ActiveObjectsManager]
    private val serverLatency get() = context[Settings].serverLatency.now
    private val sclangLatency get() = context[Settings].scLangLatency.now
    private val extraLatency get() = context[Settings].extraLatency.now

    //Only inside on ScorePlayer.execute
    fun scheduleEvents(events: List<Event>, player: ScorePlayer) {
        for (ev in events.sortedBy { ev -> -ev.type.ordinal }) {
            val (type, position, inst) = ev
            if (inst.muted.now) continue
            val obj = inst.obj
            when (type) {
                Event.Type.ObjectStart -> {
                    Logger.fine("ObjectStart: $obj at $position", Logger.Category.Playback)
                    scheduleObject(obj, position, cutoff = zero, player)
//                    Thread.sleep(5) //avoid adding
                }

                Event.Type.ObjectEnd -> {
                    Logger.fine("ObjectEnd: $obj at $position", Logger.Category.Playback)
                    val startPos = position + ObjectPosition(-obj.duration, zero)
                    if (obj.duration == zero) continue
                    val active = activeObjects.remove(obj, startPos) ?: continue
                    active.stopped()
                    if (obj is TempoGridObject && obj.meter.isResolved.now) {
                        val meter = obj.meter.force()
                        player.getClock().detach(player, meter)
                    }
                }

                else -> {}
            }
            ev.scheduled = true
        }
    }

    //Only inside on ScorePlayer.execute
    fun stopPlayBackInstantly(obj: ScoreObject, pos: ObjectPosition) {
        val active = activeObjects.getActiveObject(obj, pos) ?: return
        stopObjectInstantly(active)
    }

    //Only inside on ScorePlayer.execute
    fun stopObjectInstantly(active: ActiveScoreObject) {
        if (!active.isStillActive) return
        active.stopped()
        when {
            active.obj is SoundProcess && active.obj.instrument is SynthDefObject -> {
                val name = active.superColliderName
                client.run("if ($name != nil) { $name.release; } { \"'$name' not found\".postln; }")
            }

            active.obj is SoundProcess && active.obj.instrument is ProcessDefObject -> {
                val name = active.superColliderName
                client.run("$name.stop")
            }

            active.obj is TaskObject -> {
                val name = active.superColliderName
                client.run("$name.stop;")
            }

            else -> {}
        }
    }

    //Only inside ScorePlayer.execute
    fun scheduleObject(
        obj: ScoreObject, absolutePosition: ObjectPosition,
        cutoff: Decimal, player: ScorePlayer,
        scLangLatency: Decimal = this.sclangLatency, serverLatency: Decimal = this.serverLatency,
        extraArguments: Map<ParameterDefObject, ParameterControl> = emptyMap(),
    ): ActiveScoreObject? {
        try {
            if (!obj.validate()) return null
        } catch (e: Exception) {
            Logger.error("Failed to validate $obj", e, Logger.Category.Playback)
            return null
        }
        val time = absolutePosition.time + player.loopOffset
        val scheduledTime = (time + scLangLatency - extraLatency).toString()
        if (obj is TempoGridObject && obj.meter.isResolved.now) {
            val meter = obj.meter.force()
            player.getClock().attach(player, meter, cutoff)
        }
        val activeObject = try {
            activeObjects.insert(player, obj, absolutePosition, cutoff, extraArguments)
        } catch (e: Exception) {
            Logger.error("Failed to insert $obj into active object manager", e, Logger.Category.Playback)
            return null
        }
        val placement = if (obj is SoundProcess && obj.instrument is SynthDefObject) {
            try {
                val node = SynthObjectNode(obj, activeObject)
                nodeTree.addNode(node)
            } catch (e: Exception) {
                Logger.error("Failed to insert $obj into audio flow graph", e, Logger.Category.Playback)
                return null
            }
        } else null
        val code = try {
            obj.writeCode(activeObject.uniqueName, placement, cutoff, serverLatency, extraArguments)
        } catch (e: Exception) {
            Logger.error("Failed to write code for $obj", e, Logger.Category.Playback)
        }
        if (code == "") return null
        try {
            val info = activeObject.uniqueName
            val description = "Schedule $info"
            client.send("schedule", listOf(scheduledTime, player.id, code, info), description) //TODO why does sendAsync not work?
        } catch (e: Exception) {
            Logger.error("Failed to schedule $obj", e, Logger.Category.Playback)
        }
        println("Scheduled $activeObject at $scheduledTime ($placement)")
        Logger.fine("unique name for $obj at $time: ${activeObject.uniqueName}", Logger.Category.Playback)
        Logger.fine("time for execution: ${scheduledTime}s", Logger.Category.Playback)
        return activeObject
    }

    //Only inside on ScorePlayer.execute
    fun activeObjects(time: Decimal, delta: Decimal, score: Score): List<Event> {
        val dest = mutableListOf<Event>()
        collectActiveObjects(ObjectPosition.ZERO, score, time, delta, dest)
        dest.sortWith(compareBy({ it.absolutePosition.time }, { it.absolutePosition.y })) //TODO necessary?
        return dest
    }

    private fun collectActiveObjects(
        position: ObjectPosition, score: Score, time: Decimal, delta: Decimal,
        dest: MutableList<Event>,
    ) {
        if (position.time - delta >= time) return
        for (inst in score.objectInstances) {
            if (inst.muted.now) continue
            val obj = inst.obj
            val absolutePosition = position + inst.position
            if (obj is ScoreObjectGroup) {
                collectActiveObjects(absolutePosition, obj.score, time, delta, dest)
            } else if (absolutePosition.time - delta <= time && time <= absolutePosition.time + obj.duration) {
                dest.add(Event(Event.Type.Dummy, absolutePosition, inst))
            }
        }
    }

    companion object : PublicProperty<ScoreObjectScheduler> by publicProperty("ScoreObjectScheduler")
}