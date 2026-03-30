package ponticello.ui.midi

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import com.illposed.osc.OSCMessageEvent
import com.illposed.osc.OSCMessageListener
import hextant.context.Context
import ponticello.impl.Logger
import ponticello.model.obj.AbstractContextualObject
import ponticello.model.obj.project
import ponticello.model.player.ClockObject
import ponticello.model.project.CLOCKS
import ponticello.model.project.PLAYBACK_SETTINGS
import ponticello.model.project.get
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.eval
import ponticello.sc.client.getArgument
import ponticello.ui.launcher.PonticelloLauncher.Companion.currentProject
import ponticello.ui.misc.TimeWarpPopup
import reaktive.value.ReactiveBoolean
import reaktive.value.ReactiveVariable
import reaktive.value.binding.equalTo
import reaktive.value.now
import reaktive.value.reactiveVariable

class ContextualMidiReceiver : AbstractContextualObject(), OSCMessageListener {
    private var activeContext: ReactiveVariable<MidiContext?> = reactiveVariable(null)

    private lateinit var timeWarpPopup: TimeWarpPopup

    private val attached = reactiveVariable(false)

    val isAttached: ReactiveBoolean get() = attached

    override fun initialize(context: Context) {
        context[ContextualMidiReceiver] = this
        super.initialize(context)
        timeWarpPopup = TimeWarpPopup(context)
    }

    fun attachTo(device: String) {
        attached.now = context[SuperColliderClient].eval(
            "OSCMidiForward.attachTo(\"$device\")"
        ).get().toBooleanStrictOrNull() ?: false
    }

    fun toggleActive(context: MidiContext) {
        if (activeContext.now == context) {
            activeContext.now = null
        } else {
            activeContext.now = context
        }
    }

    fun isActive(context: MidiContext) = activeContext.equalTo(context)

    override fun acceptMessage(event: OSCMessageEvent) {
        if (event.message.address != "/forward_cc") return
        val num = event.message.getArgument<Int>(0, "num") ?: return
        val value = event.message.getArgument<Int>(1, "val") ?: return
        val ctx = activeContext.now?.takeIf { it.canReceiveMidi }
        try {
            cc(ctx, num, value)
        } catch (e: Exception) {
            Logger.error("Exception while processing MIDI message from /forward_cc", e, Logger.Category.Midi)
        }
    }

    private fun cc(
        ctx: MidiContext?,
        index: Int, midiDelta: Int
    ) {
        if (!context.hasProperty(currentProject)) return
        val djMode = context.project[PLAYBACK_SETTINGS].djMode
        if (index - CC_INDEX_OFFSET == 5 && djMode.activated.now) {
            val clock = context.project[CLOCKS].getDefault()
            clock.timeWarp.adjustByMidiDelta(midiDelta, ClockObject.TIME_WARP_SPEC, context, "Adjust playback speed")
            timeWarpPopup.update(clock.timeWarp.now)
        } else {
            ctx?.cc(index - CC_INDEX_OFFSET, midiDelta)
        }
    }

    companion object : PublicProperty<ContextualMidiReceiver> by publicProperty("Midi receiver") {
        private const val CC_INDEX_OFFSET = 20

        private const val GRID_INDEX_OFFSET = 36

        private fun xjamGridIndex(midiPitch: Int): Int {
            val index = midiPitch - GRID_INDEX_OFFSET
            val row = 3 - (index / 4)
            val column = index % 4
            return row * 4 + column
        }
    }
}