package ponticello.model

import hextant.context.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.Decimal
import ponticello.impl.copy
import ponticello.model.live.DjMode
import ponticello.model.obj.AbstractContextualObject
import ponticello.model.player.ConductorOptions
import ponticello.sc.client.SuperColliderClient
import reaktive.Observer
import reaktive.value.ReactiveVariable
import reaktive.value.forEach
import reaktive.value.now

@Serializable
class PlaybackSettings(
    val scLangLatency: ReactiveVariable<Decimal>,
    val serverLatency: ReactiveVariable<Decimal>,
    val extraLatency: ReactiveVariable<Decimal>,
    val logScCode: ReactiveVariable<Boolean>,
    val djMode: DjMode = DjMode(),
    val conductorOptions: ConductorOptions = ConductorOptions.createDefault(),
) : AbstractContextualObject() {
    val lookAhead get() = scLangLatency.now + serverLatency.now

    @Transient
    private lateinit var serverLatencyUpdater: Observer

    override fun initialize(context: Context) {
        super.initialize(context)
        val client = context[SuperColliderClient]
        serverLatencyUpdater = serverLatency.forEach { latency ->
            client.run("s.latency = $latency")
        }
        djMode.initialize(context)
    }

    companion object {
        fun createDefault(globalSettings: GlobalSettings): PlaybackSettings = PlaybackSettings(
            globalSettings.scLangLatency.copy(),
            globalSettings.serverLatency.copy(),
            globalSettings.extraLatency.copy(),
            globalSettings.logScCode.copy(),
        )
    }
}