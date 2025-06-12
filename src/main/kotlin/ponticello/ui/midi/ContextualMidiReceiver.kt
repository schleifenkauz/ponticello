package ponticello.ui.midi

import bundles.PublicProperty
import bundles.publicProperty
import javafx.scene.Node
import ponticello.impl.Logger
import ponticello.impl.MidiPitch
import ponticello.model.live.LauncherGrid
import reaktive.value.now
import java.util.*
import javax.sound.midi.*

class ContextualMidiReceiver : Receiver {
    private var device: MidiDevice? = null
    private val midiContextMap = WeakHashMap<Node, () -> MidiContext?>()

    private var launcherGrid: LauncherGrid? = null

    fun attachGrid(grid: LauncherGrid) {
        launcherGrid = grid
    }

    private fun getActiveMidiContext(): MidiContext? {
        return midiContextMap.entries
            .filter { (node, _) -> node.isFocusWithin }
            .mapNotNull { (_, context) -> context.invoke() }
            .firstOrNull()
    }

    fun registerMidiContext(node: Node, context: () -> MidiContext?) {
        midiContextMap[node] = context
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
        val note = xjamGridIndex(index)
        if (grid != null && grid.isActive.now && note in grid.items().indices) {
            val item = grid.items()[note]
            grid.noteOn(item, velocity)
        } else ctx?.noteOn(channel, MidiPitch(index), velocity)
    }

    private fun noteOff(index: Int, channel: Int, ctx: MidiContext?) {
        val grid = launcherGrid
        val note = xjamGridIndex(index)
        if (grid != null && grid.isActive.now && note in grid.items().indices) {
            grid.noteOff(grid.items()[note])
        } else ctx?.noteOff(channel, MidiPitch(index))
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

        private const val GRID_INDEX_OFFSET = 36

        private fun xjamGridIndex(midiPitch: Int): Int {
            val index = midiPitch - GRID_INDEX_OFFSET
            val row = 3 - (index / 4)
            val column = index % 4
            return row * 4 + column
        }
    }
}