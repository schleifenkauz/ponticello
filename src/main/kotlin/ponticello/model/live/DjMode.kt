package ponticello.model.live

import hextant.context.Context
import hextant.context.compoundEdit
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.randomColor
import ponticello.impl.toDecimal
import ponticello.model.flow.AudioFlowGroup
import ponticello.model.flow.AudioFlows
import ponticello.model.flow.MixerFlow
import ponticello.model.instr.BusObject
import ponticello.model.obj.AbstractContextualObject
import ponticello.model.obj.BusReference
import ponticello.model.obj.withName
import ponticello.model.registry.reference
import ponticello.model.server.BusRegistry
import ponticello.ui.dock.AppLayout
import ponticello.ui.flow.MixerPane
import ponticello.ui.launcher.PonticelloMainActivity
import reaktive.Observer
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
data class DjMode(
    val activated: ReactiveVariable<Boolean> = reactiveVariable(false)
) : AbstractContextualObject() {
    @Transient
    var selectedBus: BusReference? = null

    @Transient
    private lateinit var activationObserver: Observer

    override fun initialize(context: Context) {
        super.initialize(context)
        activationObserver = activated.observe { _, _, active ->
            if (active) {
                setupDjMode()
            }
        }
    }

    private fun getMixer(): MixerFlow? =
        context[AudioFlows].allFlows().find { f -> f.name.now == "dj_mix" } as? MixerFlow

    private fun setupDjMode() {
        if (getMixer() != null) return
        val primaryStage = context[PonticelloMainActivity].primaryStage
        val nTracks = DjModeSetupPrompt().showDialog(primaryStage) ?: return

        context.compoundEdit("Setup DJ mixer") {
            val trackBuses = mutableListOf<BusObject>()
            val buses = context[BusRegistry]
            for (i in 1..nTracks) {
                val busName = "track$i"
                if (buses.has(busName)) {
                    trackBuses.add(buses.get(busName))
                } else {
                    val bus = BusObject.audio(busName)
                    buses.add(bus, buses.size)
                    trackBuses.add(bus)
                }
            }

            val masterFlowGroup = context[AudioFlows].getOrNull("master") ?: run {
                val newGrp = AudioFlowGroup.create("master", y = 0.9.toDecimal(), color = randomColor())
                context[AudioFlows].add(newGrp)
                newGrp
            }
            val djMix = MixerFlow(
                targetBus = reactiveVariable(buses.getDefault().reference()),
                components = MixerFlow.MixerComponentList(
                    trackBuses.mapTo(mutableListOf()) { bus -> MixerFlow.MixerComponent.create(bus) }
                ),
                activateFilters = reactiveVariable(true)
            ).withName("dj_mix")
            masterFlowGroup.flows.add(djMix)
            val mixerPane = context[AppLayout].get<MixerPane>()
            mixerPane.selectMixer(djMix)
            mixerPane.setShowing(true)
        }
    }
}