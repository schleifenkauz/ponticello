package ponticello.model

import hextant.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.Decimal
import ponticello.impl.copy
import ponticello.model.flow.AudioFlows
import ponticello.model.flow.LevelMeterFlow
import ponticello.model.live.DjMode
import ponticello.model.obj.AbstractContextualObject
import ponticello.model.player.ConductorOptions
import ponticello.model.server.BusRegistry
import ponticello.sc.client.SuperColliderClient
import ponticello.ui.dock.AppLayout
import ponticello.ui.flow.TabbedAudioFlowsPane
import reaktive.Observer
import reaktive.value.*

@Serializable
class PlaybackSettings(
    val scLangLatency: ReactiveVariable<Decimal>,
    val serverLatency: ReactiveVariable<Decimal>,
    val extraLatency: ReactiveVariable<Decimal>,
    val logScCode: ReactiveVariable<Boolean>,
    val djMode: DjMode = DjMode(),
    val scrollWithPlayHead: ReactiveVariable<Boolean> = reactiveVariable(false),
    @SerialName("enableLevelMeters") private val _enableLevelMeters: ReactiveVariable<Boolean> = reactiveVariable(true),
    val conductorOptions: ConductorOptions = ConductorOptions.createDefault(),
) : AbstractContextualObject() {
    val lookAhead get() = scLangLatency.now + serverLatency.now

    val enableLevelMeters: ReactiveValue<Boolean> get() = _enableLevelMeters

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

    fun setEnableLevelMeters(enable: Boolean) {
        _enableLevelMeters.set(enable)
        context[BusRegistry].setEnableLevelMeters(enable)
        val flowsPane = context[AppLayout].get<TabbedAudioFlowsPane>()
        for (box in flowsPane.allFlowBoxes()) {
            if (box.obj is LevelMeterFlow) {
                box.setExpanded(enable)
            }
        }
        for (flow in context[AudioFlows].allFlows()) {
            if (flow is LevelMeterFlow) {
                flow.setActive(enable)
            }
        }
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