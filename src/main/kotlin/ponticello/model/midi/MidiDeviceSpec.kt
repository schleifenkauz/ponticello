package ponticello.model.midi

import hextant.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.eval
import javax.sound.midi.MidiSystem

@Serializable
sealed class MidiDeviceSpec {
    val code
        get() = when (this) {
            is None -> "nil"
            is ByName -> "\"$name\""
        }

    @Serializable
    @SerialName("None")
    data object None : MidiDeviceSpec()

    @Serializable
    @SerialName("ByName")
    data class ByName(val name: String) : MidiDeviceSpec()
    enum class Type {
        SOURCE, OUTPUT
    }

    companion object {
        fun none(): MidiDeviceSpec = None

        fun getOptions(type: Type, context: Context): List<MidiDeviceSpec> {
            val str = context[SuperColliderClient].eval("MidiTrack.${type.name.lowercase()}DevicesString").get()
            return str.removePrefix("|").split("|").map { ByName(it.trim()) }.distinct()
        }

        fun getOptions(type: Type): List<MidiDeviceSpec> {
            val classNamePrefix = when (type) {
                Type.SOURCE -> "MidiIn"
                Type.OUTPUT -> "MidiOut"
            }
            return listOf(None) + MidiSystem.getMidiDeviceInfo()
                .filter { info -> info.javaClass.simpleName.startsWith(classNamePrefix) }
                .map { info -> ByName(info.name) }
        }
    }
}