package ponticello.model.player

import bundles.PublicProperty
import bundles.publicProperty
import hextant.context.Context
import ponticello.impl.Decimal
import ponticello.impl.Logger
import ponticello.impl.zero
import ponticello.model.obj.project
import ponticello.model.project.PLAYBACK_SETTINGS
import ponticello.model.project.get
import ponticello.model.score.ObjectPosition
import ponticello.model.score.ScoreObject
import ponticello.model.score.TempoGridObject
import ponticello.sc.client.SuperColliderClient
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
                    if (obj.duration == zero) continue
                    if (obj is TempoGridObject && obj.meter.isResolved.now) {
                        val meter = obj.meter.force()
                        player.getClock().detach(player, meter)
                    }
                }

                else -> {}
            }
        }
    }

    //Only inside on ScorePlayer.execute
    fun stopPlayBackInstantly(obj: ScoreObject, pos: ObjectPosition) {
        client.run("${obj.superColliderName}.getInstanceAt($pos).release")
    }

    //Only inside on ScorePlayer.execute
    fun stopObjectInstantly(obj: ScoreObject, instanceId: Int) {
        client.run("${obj.superColliderName}.getInstance($instanceId).release")
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
        }
        if (code == "") return null
        try {
            val description = "Schedule ${obj.name.now}"
            val args = listOf(absolute, scheduledTime.toString(), info.player.id, code)
            if (playbackSettings.logScCode.now) {
                println("Schedule ${obj.name.now} at $scheduledTime, player_id = ${info.player.id}:")
                println(code)
                println("################ END #################")
            }
            return client.send("schedule", args, description).thenApply(String::toIntOrNull)
        } catch (e: Exception) {
            Logger.error("Failed to schedule $obj", e, Logger.Category.Playback)
            return null
        }
    }

    companion object : PublicProperty<ScoreObjectScheduler> by publicProperty("ScoreObjectScheduler")
}