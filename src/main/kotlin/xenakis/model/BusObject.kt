package xenakis.model

import hextant.context.Context
import javafx.scene.input.DataFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.Observer
import reaktive.value.*
import xenakis.impl.SuperColliderClient
import xenakis.sc.Rate
import xenakis.sc.editor.AbstractRenamableObject
import xenakis.ui.XenakisController

@Serializable
class BusObject(
    override val mutableName: ReactiveVariable<String>,
    val rate: ReactiveValue<Rate>,
    val channels: ReactiveValue<Int>,
    val isOutput: Boolean = false
) : AbstractRenamableObject() {
    val variableName get() = if (isOutput) "0" else "~bus_${name.now}"

    val allocationCode: String
        get() = "$variableName = Bus.${rate.now.name.lowercase()}(s, ${channels.now})"

    val deallocationCode: String
        get() = "$variableName.free; $variableName = nil"

    @Transient
    private lateinit var observer: Observer

    override fun canRenameTo(newName: String): Boolean =
        name.now.startsWith("global_") == newName.startsWith("global_") &&
                !context[XenakisController.currentProject].busses.has(newName)

    override fun rename(newName: String) {
        context[SuperColliderClient].run("~bus_$newName = $variableName; $variableName = nil;")
        super.rename(newName)
    }

    override fun initialize(context: Context) {
        super.initialize(context)
        if (!isOutput) {
            context[SuperColliderClient].run(allocationCode)
            observer = rate.observe { _ -> reallocate() } and channels.observe { _ -> reallocate() }
        }
    }

    fun reallocate() {
        if (isOutput) return
        context[SuperColliderClient].run {
            +"if ($variableName != nil) { $deallocationCode }"
            +allocationCode
        }
    }

    fun removed() {
        context[SuperColliderClient].run(deallocationCode)
    }

    override fun createReference(): BusObjectReference = BusObjectReference(this)

    companion object {
        val output = BusObject(
            reactiveVariable("output"),
            reactiveValue(Rate.Audio),
            reactiveVariable(2),
            isOutput = true
        )

        fun create(name: String, rate: Rate = Rate.Audio, channels: Int = 2) =
            BusObject(reactiveVariable(name), reactiveVariable(rate), reactiveVariable(channels))

        val DATA_FORMAT = DataFormat("bus")
    }
}