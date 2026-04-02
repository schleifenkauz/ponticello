package ponticello.ui.midi

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import ponticello.model.obj.project
import ponticello.model.player.ClockObject
import ponticello.model.project.CLOCKS
import ponticello.model.project.PLAYBACK_SETTINGS
import ponticello.model.project.get
import ponticello.ui.launcher.PonticelloLauncher.Companion.currentProject
import ponticello.ui.misc.TimeWarpPopup
import reaktive.value.ReactiveVariable
import reaktive.value.binding.equalTo
import reaktive.value.now
import reaktive.value.reactiveVariable

class ContextualMidiReceiver : OSCMidiListener() {
    private var activeContext: ReactiveVariable<MidiContext?> = reactiveVariable(null)

    private lateinit var timeWarpPopup: TimeWarpPopup

    override fun initialize(context: Context) {
        context[ContextualMidiReceiver] = this
        super.initialize(context)
        timeWarpPopup = TimeWarpPopup(context)
    }

    fun toggleActive(context: MidiContext) {
        if (activeContext.now == context) {
            activeContext.now = null
        } else {
            activeContext.now = context
        }
    }

    fun isActive(context: MidiContext) = activeContext.equalTo(context)

    override fun controlChange(index: Int, value: Int) {
        val ctx = activeContext.now?.takeIf { it.canReceiveMidi }
        if (!context.hasProperty(currentProject)) return
        val djMode = context.project[PLAYBACK_SETTINGS].djMode
        if (index - CC_INDEX_OFFSET == 5 && djMode.activated.now) {
            val clock = context.project[CLOCKS].getDefault()
            clock.timeWarp.adjustByMidiDelta(value, ClockObject.TIME_WARP_SPEC, context, "Adjust playback speed")
            timeWarpPopup.update(clock.timeWarp.now)
        } else {
            ctx?.cc(index - CC_INDEX_OFFSET, value)
        }
    }

    private fun cc(ctx: MidiContext?, index: Int, midiDelta: Int) {
    }

    companion object : PublicProperty<ContextualMidiReceiver> by publicProperty("Midi receiver") {
        private const val CC_INDEX_OFFSET = 20
    }
}