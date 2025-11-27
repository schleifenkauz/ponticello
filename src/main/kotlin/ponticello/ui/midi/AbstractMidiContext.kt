package ponticello.ui.midi

import hextant.context.Context
import reaktive.value.ReactiveBoolean

abstract class AbstractMidiContext(final override val context: Context) : MidiContext {
    private var condition = { true }

    override val canReceiveMidi: Boolean get() = condition()

    override val isActive: ReactiveBoolean = context[ContextualMidiReceiver].isActive(this)

    override fun setCondition(condition: () -> Boolean) {
        this.condition = condition
    }

    override fun toggleActive() {
        context[ContextualMidiReceiver].toggleActive(this)
    }
}