package ponticello.ui.midi

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.model.obj.AbstractNamedObject
import ponticello.sc.client.SuperColliderClient
import reaktive.value.*

@Serializable
sealed class MidiInstrument : AbstractNamedObject() {
    @Transient
    final override val name: ReactiveString = reactiveValue("midi_instrument${idCounter++}")

    private val enabled = reactiveVariable(true)

    val isEnabled: ReactiveBoolean get() = enabled

    fun toggleEnabled() {
        enabled.set(!enabled.now)
        context[SuperColliderClient].run("$superColliderName.enabled = ${isEnabled.now}")
    }

    override fun onRemoved() {
        context[SuperColliderClient].run("$superColliderName = nil")
    }

    val superColliderName: String get() = "~${name.now}"

    abstract override fun copy(): MidiInstrument

    companion object {
        private var idCounter = 0
    }

}