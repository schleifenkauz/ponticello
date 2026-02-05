package ponticello.ui.midi

import bundles.PublicProperty
import bundles.publicProperty
import hextant.context.Context
import ponticello.impl.Logger
import ponticello.impl.MidiPitch
import ponticello.model.live.LauncherGrid
import ponticello.model.obj.AbstractContextualObject
import ponticello.model.obj.project
import ponticello.model.player.ClockObject
import ponticello.model.project.CLOCKS
import ponticello.model.project.LAUNCHER_GRID
import ponticello.model.project.PLAYBACK_SETTINGS
import ponticello.model.project.get
import ponticello.ui.launcher.PonticelloLauncher.Companion.currentProject
import ponticello.ui.misc.TimeWarpPopup
import reaktive.value.ReactiveBoolean
import reaktive.value.ReactiveVariable
import reaktive.value.binding.equalTo
import reaktive.value.now
import reaktive.value.reactiveVariable
import javax.sound.midi.*

class ContextualMidiReceiver : Receiver, AbstractContextualObject() {
    private var device: MidiDevice? = null
    private val launcherGrid: LauncherGrid?
        get() {
            if (!initialized) return null
            if (!context.hasProperty(currentProject)) return null
            return context.project[LAUNCHER_GRID]
        }

    private var activeContext: ReactiveVariable<MidiContext?> = reactiveVariable(null)

    private lateinit var timeWarpPopup: TimeWarpPopup

    private val attached = reactiveVariable(false)

    val isAttached: ReactiveBoolean get() = attached

    override fun initialize(context: Context) {
        super.initialize(context)
        timeWarpPopup = TimeWarpPopup(context)
    }

    fun attachTo(deviceName: String) {
        val devices = MidiSystem.getMidiDeviceInfo()
        Logger.info("Available MIDI devices: ${devices.joinToString { d -> d.name }}", Logger.Category.VSTPlugins)
        val info = devices.find { d -> d.name.startsWith(deviceName) && d.javaClass.simpleName.startsWith("MidiIn") }
        if (info == null) {
            Logger.info("No Xjam device available", Logger.Category.Playback)
            return
        }
        val device = MidiSystem.getMidiDevice(info)
        this.device = device
        attached.set(device != null)
        if (device == null) {
            Logger.error("MidiSystem.getMidiDevice returned null for device '${info.name}'")
            return
        }
        try {
            device.open()
            device.transmitter.receiver = this
        } catch (e: Exception) {
            Logger.error("Exception while attempting to open midi device '${info.name}'", e)
        }
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
                ShortMessage.NOTE_ON -> {
                    if (velocity == 0) noteOff(index, message.channel, ctx)
                    else noteOn(index, velocity, message.channel, ctx)
                }

                ShortMessage.NOTE_OFF -> noteOff(index, message.channel, ctx)
                ShortMessage.CONTROL_CHANGE -> cc(ctx, message, index, velocity)
            }
        } catch (e: Exception) {
            val deviceName = device!!.deviceInfo.name
            val message = message.message?.asList()
            Logger.error("Exception while processing MIDI message from $deviceName: $message", e, Logger.Category.Midi)
        }
    }

    private fun noteOn(index: Int, velocity: Int, channel: Int, ctx: MidiContext?) {
        val grid = launcherGrid?.takeIf { it.isActive.now }
        val note = xjamGridIndex(index)
        if (grid != null && grid.isActive.now && note in grid.items().indices) {
            val item = grid.items()[note]
            grid.noteOn(item, velocity)
        } else ctx?.noteOn(channel, MidiPitch(index), velocity)
    }

    private fun noteOff(index: Int, channel: Int, ctx: MidiContext?) {
        val grid = launcherGrid?.takeIf { it.isActive.now }
        val note = xjamGridIndex(index)
        if (grid != null && note in grid.items().indices) {
            grid.noteOff(grid.items()[note])
        } else ctx?.noteOff(channel, MidiPitch(index))
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