package ponticello.model.player

import bundles.PublicProperty
import bundles.publicProperty
import hextant.context.Context
import ponticello.impl.*
import ponticello.model.instr.MidiInstrument
import ponticello.model.instr.RoutineDefObject
import ponticello.model.obj.project
import ponticello.model.project.PLAYBACK_SETTINGS
import ponticello.model.project.get
import ponticello.model.score.*
import ponticello.sc.client.ScWriter
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.run
import ponticello.sc.client.schedule
import reaktive.value.now
import java.util.concurrent.CompletableFuture

class ScoreObjectScheduler(val context: Context) {
    private val client = context[SuperColliderClient]
    private val playbackSettings by lazy { context.project[PLAYBACK_SETTINGS] }
    val serverLatency get() = playbackSettings.serverLatency.now
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
                    val info = ObjectPlaybackInfo(position, player, instance = inst)
                    scheduleObject(obj, info)
                }

                ScoreEvent.Type.ObjectEnd -> {
                    Logger.fine("ObjectEnd: $obj at $position", Logger.Category.Playback)
                    objectEnd(obj, player, position, inst)
                }

                else -> {}
            }
        }
    }

    private fun objectEnd(obj: ScoreObject, player: ScorePlayer, pos: ObjectPosition, instance: ScoreObjectInstance) {
        when (obj) {
            is TempoGridObject -> {
                if (obj.meter.isResolved.now) {
                    val meter = obj.meter.force()
                    player.getClock().detach(player, meter)
                }
            }

            is SoundProcess -> {
                val time = pos.time + player.timeOffset
                when (obj.getInstrument()) {
                    is RoutineDefObject -> {
                        val objectStart = pos.plusTime(-obj.duration)
                        val code = writeCode {
                            releaseObject(obj, objectStart)
                        }
                        val description = "stop ${obj.name.now}"
                        client.schedule(description, time, absolute = false, player, code)
                    }

                    is MidiInstrument -> {
                        val parent = instance.score?.parentObject
                        if (parent is MidiObject) {
                            val midinote = instance.y
                            val code = writeCode {
                                val track = parent.track.now.get() ?: return@writeCode
                                track.run { sendNoteOff(midinote, obj.controls, serverLatency) }
                            }
                            client.schedule("noteOff $midinote", time, absolute = false, player, code)
                        }
                    }

                    else -> {}
                }
            }

            else -> {}
        }
    }

    //Only inside on ScorePlayer.execute
    fun stopObjectInstantly(instance: ScoreObjectInstance, pos: ObjectPosition) {
        val parentObject = instance.score?.parentObject
        val obj = instance.obj
        client.run {
            if (parentObject is MidiObject && obj is SoundProcess && obj.getInstrument() is MidiInstrument) {
                val track = parentObject.track.now.get() ?: return@run
                val midinote = instance.y
                track.run { sendNoteOff(midinote, obj.controls, latency = zero) }
            } else {
                releaseObject(obj, pos)
            }
        }
    }

    //Only inside on ScorePlayer.execute
    fun stopObjectInstantly(obj: ScoreObject, instanceId: Int) {
        client.run("${obj.superColliderName}.getInstance($instanceId).release")
    }

    private fun ScWriter.releaseObject(obj: ScoreObject, pos: ObjectPosition) {
        +"${obj.superColliderName}.getInstanceAt($pos).release"
    }

    //Only inside ScorePlayer.execute
    fun scheduleObject(
        obj: ScoreObject, info: ObjectPlaybackInfo, scLangLatency: Decimal = this.sclangLatency,
    ): CompletableFuture<Int?>? {
        val time = info.pos.time + info.cutoff + info.player.timeOffset
        val scheduledTime = (time + scLangLatency - extraLatency)
        return scheduleObject(obj, info, scheduledTime, absolute = false)
    }

    fun scheduleObject(
        obj: ScoreObject, info: ObjectPlaybackInfo,
        scheduledTime: Decimal, absolute: Boolean,
    ): CompletableFuture<Int?>? {
        try {
            if (!obj.validate()) return null
        } catch (e: Exception) {
            Logger.error("Failed to validate $obj", e, Logger.Category.Playback)
            return null
        }
        if (obj is TempoGridObject && obj.meter.isResolved.now) {
            val meter = obj.meter.force()
            info.player.getClock().attach(info.player, meter, info.cutoff)
        }
        if (!obj.affectsPlayback) return null
        val code = try {
            obj.startNewInstance(info)
        } catch (e: Exception) {
            Logger.error("Failed to write code for $obj", e, Logger.Category.Playback)
            return null
        }
        if (code == "") return null
        try {
            val description = "start ${obj.name.now}"
            return client.schedule(description, scheduledTime, absolute, info.player, code)
                .thenApply(String::toIntOrNull)
        } catch (e: Exception) {
            Logger.error("Failed to schedule $obj", e, Logger.Category.Playback)
            return null
        }
    }

    companion object : PublicProperty<ScoreObjectScheduler> by publicProperty("ScoreObjectScheduler")
}