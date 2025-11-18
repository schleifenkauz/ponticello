package ponticello.model.player

import bundles.PublicProperty
import bundles.publicProperty
import hextant.context.Context
import ponticello.impl.Decimal
import ponticello.impl.Logger
import ponticello.impl.unaryMinus
import ponticello.impl.zero
import ponticello.model.flow.ActiveObjectNode
import ponticello.model.flow.NodeTree
import ponticello.model.obj.*
import ponticello.model.project.PLAYBACK_SETTINGS
import ponticello.model.project.get
import ponticello.model.score.*
import ponticello.model.score.controls.ParameterControl
import ponticello.sc.client.SuperColliderClient
import reaktive.value.now

class ScoreObjectScheduler(val context: Context) {
    private val client = context[SuperColliderClient]
    private val nodeTree = context[NodeTree]
    private val activeObjects = context[ActiveObjectsManager]
    private val playbackSettings by lazy { context.project[PLAYBACK_SETTINGS] }
    private val serverLatency get() = playbackSettings.serverLatency.now
    private val sclangLatency get() = playbackSettings.scLangLatency.now
    private val extraLatency get() = playbackSettings.extraLatency.now

    //Only inside on ScorePlayer.execute
    fun scheduleEvents(events: List<ScoreEvent>, player: ScorePlayer) {
        for (ev in events.sortedBy { ev -> -ev.type.ordinal }) {
            val (type, position, inst) = ev
            if (inst.muted.now) continue
            val obj = inst.obj
            when (type) {
                ScoreEvent.Type.ObjectStart -> {
                    Logger.fine("ObjectStart: $obj at $position", Logger.Category.Playback)
                    scheduleObject(obj, inst, position, cutoff = zero, player)
                }

                ScoreEvent.Type.ObjectEnd -> {
                    Logger.fine("ObjectEnd: $obj at $position", Logger.Category.Playback)
                    val startPos = position + ObjectPosition(-obj.duration, zero)
                    if (obj.duration == zero) continue
                    if (obj is TempoGridObject && obj.meter.isResolved.now) {
                        val meter = obj.meter.force()
                        player.getClock().detach(player, meter)
                    }
                    val active = activeObjects.remove(obj, startPos)
                    active?.stopped()
                }

                else -> {}
            }
        }
    }

    //Only inside on ScorePlayer.execute
    fun stopPlayBackInstantly(obj: ScoreObject, pos: ObjectPosition) {
        val active = activeObjects.getActiveObject(obj, pos) ?: return
        stopObjectInstantly(active)
    }

    //Only inside on ScorePlayer.execute
    fun stopObjectInstantly(active: ActiveScoreObject) {
        if (!active.isStillActive) {
            println("$active is not active anymore")
            return
        }
        active.stopped()
        when {
            active.obj is SoundProcess && active.obj.def is SynthDefObject -> {
                val name = active.superColliderName
                client.run("if ($name != nil) { $name.release; } { \"'$name' not found\".postln; }")
            }

            active.obj is SoundProcess && active.obj.def is ProcessDefObject -> {
                val name = active.superColliderName
                client.run("$name.stop")
            }

            active.obj is SoundProcess && active.obj.def is VSTInstrumentObject -> {
                val instrument = active.obj.def as VSTInstrumentObject
                val controllerVar = instrument.flow.controllerVar
                val midinote = active.instance?.y ?: return
                client.run("$controllerVar.midi.noteOff(0, $midinote)")
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
        obj: ScoreObject, instance: ScoreObjectInstance?, absolutePosition: ObjectPosition,
        cutoff: Decimal, player: ScorePlayer,
        scLangLatency: Decimal = this.sclangLatency, serverLatency: Decimal = this.serverLatency,
        extraArguments: Map<ParameterDefObject, ParameterControl> = emptyMap(),
    ): ActiveScoreObject? {
        val time = absolutePosition.time + cutoff + player.timeOffset
        val scheduledTime = (time + scLangLatency - extraLatency)
        return scheduleObject(
            obj, instance, player, cutoff, absolutePosition,
            serverLatency, scheduledTime, absolute = false,
            extraArguments
        )
    }

    fun scheduleObject(
        obj: ScoreObject, instance: ScoreObjectInstance?, player: ScorePlayer,
        cutoff: Decimal, absolutePosition: ObjectPosition,
        serverLatency: Decimal, scheduledTime: Decimal, absolute: Boolean,
        extraArguments: Map<ParameterDefObject, ParameterControl>,
    ): ActiveScoreObject? {
        try {
            if (!obj.validate()) return null
        } catch (e: Exception) {
            Logger.error("Failed to validate $obj", e, Logger.Category.Playback)
            return null
        }
        if (obj is TempoGridObject && obj.meter.isResolved.now) {
            val meter = obj.meter.force()
            player.getClock().attach(player, meter, cutoff)
        }
        if (!obj.affectsPlayback) return null
        val activeObject = try {
            activeObjects.insert(player, obj, instance, absolutePosition, cutoff, extraArguments)
        } catch (e: Exception) {
            Logger.error("Failed to insert $obj into active object manager", e, Logger.Category.Playback)
            return null
        }
        val placement = when {
            obj is SoundProcess && obj.def is SynthDefObject -> {
                try {
                    val node = ActiveObjectNode(obj, activeObject)
                    nodeTree.addNode(node)
                } catch (e: Exception) {
                    Logger.error("Failed to insert $obj into audio flow graph", e, Logger.Category.Playback)
                    return null
                }
            }

            else -> null
        }
        val code = try {
            obj.writeCode(instance, activeObject.uniqueName, placement, cutoff, serverLatency, extraArguments)
        } catch (e: Exception) {
            Logger.error("Failed to write code for $obj", e, Logger.Category.Playback)
        }
        if (code == "") return null
        try {
            val info = activeObject.uniqueName
            val description = "Schedule $info"
            //TODO why does sendAsync not work?
            client.send("schedule", listOf(absolute, scheduledTime.toString(), player.id, code, info), description)
        } catch (e: Exception) {
            Logger.error("Failed to schedule $obj", e, Logger.Category.Playback)
        }
        return activeObject
    }

    companion object : PublicProperty<ScoreObjectScheduler> by publicProperty("ScoreObjectScheduler")
}