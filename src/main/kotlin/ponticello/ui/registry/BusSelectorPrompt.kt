package ponticello.ui.registry

import ponticello.model.instr.BusObject
import ponticello.model.obj.project
import ponticello.model.project.SERVER_OPTIONS
import ponticello.model.project.get
import ponticello.model.registry.chooseTargetMixer
import ponticello.model.server.BusRegistry
import ponticello.sc.Rate
import reaktive.value.now

class BusSelectorPrompt(
    registry: BusRegistry, title: String,
    private val rate: Rate? = null, private val channels: Int? = null
) : RegistrySelectorPrompt<BusObject>(registry, title) {
    override val canCreateItem: Boolean get() = true

    override fun options(): List<BusObject> = super.options()
        .filter { bus ->
            (rate == null || bus.rate == rate) && (channels == null || bus.channels.now == channels)
        }

    override fun displayText(option: BusObject): String =
        "${option.name.now}: ${option.channels.now} x ${option.rate}"

    override fun extractText(option: BusObject): String = option.name.now

    override fun createObject(name: String): BusObject {
        val rate = rate ?: Rate.Audio
        val channels = channels ?: if (rate == Rate.Audio) registry.context.project[SERVER_OPTIONS].numOutputChannels else 1
        return BusObject.create(rate, name, channels).chooseTargetMixer(registry.context.project, this.placement)
    }
}