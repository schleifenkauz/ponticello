package xenakis.ui.midi

import bundles.PublicProperty
import bundles.publicProperty
import javafx.stage.Window
import reaktive.value.now
import xenakis.impl.Logger
import xenakis.impl.MidiPitch
import xenakis.model.live.LauncherGrid
import java.util.*
import javax.sound.midi.*

class ContextualMidiReceiver : Receiver {
    private var device: MidiDevice? = null
    private val midiContextMap = WeakHashMap<Window, () -> MidiContext?>()

    private var launcherGrid: LauncherGrid? = null

    fun attachGrid(grid: LauncherGrid) {
        launcherGrid = grid
    }

    private fun getActiveMidiContext(): MidiContext? {
        val activeWindow = Window.getWindows().firstOrNull { w -> w.isFocused } ?: return null
        return midiContextMap[activeWindow]?.invoke()
    }

    fun registerMidiContext(window: Window, context: () -> MidiContext?) {
        midiContextMap[window] = context
    }

    override fun send(message: MidiMessage?, timeStamp: Long) {
        if (message !is ShortMessage) return
        val ctx = getActiveMidiContext()
        val index = message.data1
        val velocity = message.data2
        when (message.command) {
            ShortMessage.NOTE_ON -> {
                if (velocity == 0) noteOff(index, message.channel, ctx)
                else noteOn(index, velocity, message.channel, ctx)
            }

            ShortMessage.NOTE_OFF -> noteOff(index, message.channel, ctx)
            ShortMessage.CONTROL_CHANGE -> ctx?.cc(message.channel, index - CC_INDEX_OFFSET, velocity)
        }
    }

    private fun noteOn(index: Int, velocity: Int, channel: Int, ctx: MidiContext?) {
        val grid = launcherGrid
        val note = index - NOTE_INDEX_OFFSET
        if (grid != null && grid.isActive.now && note in grid.itemIndices) grid.noteOn(note, velocity)
        else ctx?.noteOn(channel, MidiPitch(index), velocity)
    }

    private fun noteOff(index: Int, channel: Int, ctx: MidiContext?) {
        val grid = launcherGrid
        val note = index - NOTE_INDEX_OFFSET
        if (grid != null && grid.isActive.now && note in grid.itemIndices) grid.noteOff(note)
        else ctx?.noteOff(channel, MidiPitch(index))
    }

    fun initialize(deviceName: String) {
        val devices = MidiSystem.getMidiDeviceInfo()
        Logger.info("Available MIDI devices: ${devices.joinToString { d -> d.name }}", Logger.Category.VSTPlugins)
        for (info in devices.filter { d -> d.name.startsWith(deviceName) }) {
            device = MidiSystem.getMidiDevice(info)
            try {
                device!!.open()
                device!!.transmitter.receiver = this
                break
            } catch (e: Exception) {
                System.err.println("Exception while attempting to open midi device ${info.name}: ${e.message}")
                continue
            }
        }
    }

    override fun close() {
        device?.close()
    }

    companion object : PublicProperty<ContextualMidiReceiver> by publicProperty("Midi receiver") {
        private const val CC_INDEX_OFFSET = 20

        private const val NOTE_INDEX_OFFSET = 48
    }
}