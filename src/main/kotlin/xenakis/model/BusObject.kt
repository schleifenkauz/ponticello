package xenakis.model

import hextant.context.Context
import javafx.scene.input.DataFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.Observer
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.ScWriter
import xenakis.model.SuperColliderObject.LiveCycleType
import xenakis.sc.Rate
import xenakis.ui.XenakisController

@Serializable
class BusObject(
    override val mutableName: ReactiveVariable<String>,
    val rate: ReactiveVariable<Rate>,
    val channels: ReactiveVariable<Int>,
    val isOutput: Boolean = false
) : AbstractSuperColliderObject() {
    override val variableName get() = if (isOutput) "0" else "~bus_${name.now}"

    override val liveCycleType: LiveCycleType
        get() = LiveCycleType.ServerBoot

    @Transient
    private lateinit var observer: Observer

    override fun ScWriter.allocateServerObject() {
        if (!isOutput) +"$variableName = Bus.${rate.now.name.lowercase()}(s, ${channels.now})"
    }

    override fun canRenameTo(newName: String): Boolean =
        name.now.startsWith("global_") == newName.startsWith("global_") &&
                !context[XenakisController.currentProject].busses.has(newName)

    override fun initialize(context: Context) {
        if (initialized) return
        super.initialize(context)
        if (!isOutput) {
            observer = rate.observe { _ -> redefine() } and channels.observe { _ -> redefine() }
        }
    }

    override fun createReference(): BusObjectReference = BusObjectReference(this)

    companion object {
        val output = BusObject(
            reactiveVariable("output"),
            reactiveVariable(Rate.Audio),
            reactiveVariable(2),
            isOutput = true
        )

        fun create(name: String, rate: Rate = Rate.Audio, channels: Int = 2) =
            BusObject(reactiveVariable(name), reactiveVariable(rate), reactiveVariable(channels))

        val DATA_FORMAT = DataFormat("bus")
    }
}