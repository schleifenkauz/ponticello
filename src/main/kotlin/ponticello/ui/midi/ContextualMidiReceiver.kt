package ponticello.ui.midi

import bundles.PublicProperty
import bundles.publicProperty
import hextant.context.Context
import ponticello.impl.Logger
import ponticello.model.obj.AbstractContextualObject
import ponticello.model.obj.project
import ponticello.model.player.ClockObject
import ponticello.model.project.CLOCKS
import ponticello.model.project.PLAYBACK_SETTINGS
import ponticello.model.project.get
import ponticello.ui.launcher.PonticelloLauncher.Companion.currentProject
import ponticello.ui.misc.TimeWarpPopup
import reaktive.value.ReactiveBoolean
import reaktive.value.ReactiveVariable
import reaktive.value.binding.equalTo
import reaktive.value.now
import reaktive.value.reactiveVariable
import javax.sound.midi.MidiDevice
import javax.sound.midi.MidiMessage
import javax.sound.midi.Receiver
import javax.sound.midi.ShortMessage

class ContextualMidiReceiver : Receiver, AbstractContextualObject() {
    private var device: MidiDevice? = null

    private var activeContext: ReactiveVariable<MidiContext?> = reactiveVariable(null)

    private lateinit var timeWarpPopup: TimeWarpPopup

    private val attached = reactiveVariable(false)

    val isAttached: ReactiveBoolean get() = attached

    override fun initialize(context: Context) {
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

    override fun send(message: MidiMessage, timeStamp: Long) {
        try {
            if (message !is ShortMessage) return
            val ctx = activeContext.now?.takeIf { it.canReceiveMidi }
            val index = message.data1
            val velocity = message.data2
            when (message.command) {
                ShortMessage.CONTROL_CHANGE -> cc(ctx, message, index, velocity)
            }
        } catch (e: Exception) {
            val deviceName = device!!.deviceInfo.name
            val message = message.message?.asList()
            Logger.error("Exception while processing MIDI message from $deviceName: $message", e, Logger.Category.Midi)
        }
    }

    private fun cc(
        ctx: MidiContext?, message: ShortMessage,
        index: Int, midiDelta: Int
    ) {
        if (!context.hasProperty(currentProject)) return
        val djMode = context.project[PLAYBACK_SETTINGS].djMode
        if (index - CC_INDEX_OFFSET == 5 && djMode.activated.now) {
            val clock = context.project[CLOCKS].getDefault()
            clock.timeWarp.adjustByMidiDelta(midiDelta, ClockObject.TIME_WARP_SPEC, context, "Adjust playback speed")
            timeWarpPopup.update(clock.timeWarp.now)
        } else {
            ctx?.cc(message.channel, index - CC_INDEX_OFFSET, midiDelta)
        }
    }

    override fun close() {
        device?.close()
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