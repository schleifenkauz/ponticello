package xenakis.model.obj

import hextant.context.Context
import javafx.scene.input.DataFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.Observer
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.Point
import xenakis.model.obj.SuperColliderObject.LiveCycleType
import xenakis.sc.Rate
import xenakis.sc.client.ScWriter
import xenakis.ui.launcher.XenakisLauncher

@Serializable
class BusObject(
    override val mutableName: ReactiveVariable<String>,
    val rate: ReactiveVariable<Rate>,
    val channels: ReactiveVariable<Int>,
    val type: Type = Type.Regular,
    val positionInGraph: ReactiveVariable<Point>
) : AbstractSuperColliderObject() {
    override val superColliderName
        get() = when (type) {
            Type.Input -> "s.inputBus"
            Type.Output -> "s.outputBus"
            Type.Regular -> "~bus_${name.now}"
        }

    override val functionName
        get() = when (type) {
            Type.Output -> "~output_bus_init"
            Type.Input -> "~input_bus_init"
            Type.Regular -> "~bus_${name.now}"
        }

    override val liveCycleType: LiveCycleType
        get() = LiveCycleType.ServerBoot

    @Transient
    private lateinit var observer: Observer

    override fun ScWriter.allocateServerObject() {
        when (type) {
            Type.Input -> {}
            Type.Output -> {}
            Type.Regular -> +"$superColliderName = Bus.${rate.now.name.lowercase()}(s, ${channels.now})"
        }
    }

    override fun canRenameTo(newName: String): Boolean =
        name.now.startsWith("global_") == newName.startsWith("global_") &&
                !context[XenakisLauncher.currentProject].busses.has(newName)

    override fun initialize(context: Context) {
        if (initialized) return
        super.initialize(context)
        if (type == Type.Regular) {
            observer = rate.observe { _ -> redefine() } and channels.observe { _ -> redefine() }
        }
    }

    enum class Type {
        Regular, Input, Output;
    }

    companion object {
        val input = BusObject(
            reactiveVariable("input"),
            reactiveVariable(Rate.Audio),
            reactiveVariable(2),
            type = Type.Input,
            reactiveVariable(Point(0.0, 0.0))
        )

        val output = BusObject(
            reactiveVariable("output"),
            reactiveVariable(Rate.Audio),
            reactiveVariable(2),
            type = Type.Output,
            reactiveVariable(Point(0.0, 0.0)) //TODO
        )

        fun create(name: String, rate: Rate = Rate.Audio, channels: Int = 2, positionInGraph: Point = Point(0.0, 0.0)) =
            BusObject(
                reactiveVariable(name),
                reactiveVariable(rate),
                reactiveVariable(channels),
                Type.Regular,
                reactiveVariable(positionInGraph)
            )

        val DATA_FORMAT = DataFormat("bus")
    }
}