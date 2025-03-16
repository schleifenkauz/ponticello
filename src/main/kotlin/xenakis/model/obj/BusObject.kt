package xenakis.model.obj

import hextant.context.Context
import javafx.scene.input.DataFormat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.Observer
import reaktive.Reactive
import reaktive.value.ReactiveVariable
import reaktive.value.binding.map
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.zero
import xenakis.model.registry.BusRegistry
import xenakis.model.registry.ObjectRegistry
import xenakis.sc.NumericalControlSpec
import xenakis.sc.Rate
import xenakis.sc.Rate.Audio
import xenakis.sc.Rate.Control
import xenakis.sc.client.ScWriter
import xenakis.ui.launcher.XenakisLauncher

@Serializable
sealed class BusObject : AbstractSuperColliderObject() {
    abstract val rate: Rate
    abstract val channels: ReactiveVariable<Int>
    abstract val type: Type

    override val superColliderName
        get() = when (type) {
            Type.Input -> "s.inputBus"
            Type.Output -> "s.outputBus"
            Type.Regular -> "~bus_${name.now}"
        }

    override val canRename: Boolean
        get() = type == Type.Regular

    override val canDelete: Boolean
        get() = type == Type.Regular

    override val registry: ObjectRegistry<*>?
        get() = context[BusRegistry]

    @Transient
    private lateinit var observer: Observer

    protected abstract fun observables(): Collection<Reactive>

    override fun canRenameTo(newName: String): Boolean =
        name.now.startsWith("global_") == newName.startsWith("global_") &&
                !context[XenakisLauncher.currentProject].busses.has(newName)

    override fun initialize(context: Context) {
        if (initialized) return
        super.initialize(context)
        if (type == Type.Regular) {
            observer = channels.observe { _ -> redefine() }
        }
    }

    enum class Type {
        Regular, Input, Output;
    }

    @SerialName("AudioBus")
    class AudioBus(
        override val mutableName: ReactiveVariable<String>,
        override val channels: ReactiveVariable<Int>,
        override val type: Type
    ) : BusObject() {
        override val rate: Rate = Audio

        override fun ScWriter.createObject() {
            when (type) {
                Type.Input -> {}
                Type.Output -> {}
                Type.Regular -> {
                    +"$superColliderName = Bus.ar(s, $channels.now)}"
                }
            }
        }

        override fun observables(): Collection<Reactive> = listOf(channels)
    }

    @Serializable
    @SerialName("ControlBus")
    class ControlBus(
        override val mutableName: ReactiveVariable<String>,
        override val channels: ReactiveVariable<Int>,
        val spec: ReactiveVariable<NumericalControlSpec>,
    ) : BusObject() {
        override val rate: Rate = Control
        override val type: Type
            get() = Type.Regular
        val defaultValue = spec.map { s -> s.defaultValue.get() }

        override fun ScWriter.createObject() {
            +"$superColliderName = Bus.kr(s, ${channels.now})"
            val value = defaultValue.now
            if (value == zero) return
            val valueList = List(channels.now) { value }.joinToString()
            +"$superColliderName.set($valueList)"
        }

        override fun observables(): Collection<Reactive> = listOf(channels, defaultValue)
    }

    companion object {
        val input = AudioBus(
            reactiveVariable("input"),
            reactiveVariable(2),
            type = Type.Input,
        )

        val output = AudioBus(
            reactiveVariable("output"),
            reactiveVariable(2),
            type = Type.Output,
        )

        fun audio(name: String, channels: Int = 2) = AudioBus(
            reactiveVariable(name),
            reactiveVariable(channels),
            Type.Regular
        )

        fun control(name: String, channels: Int = 1, spec: ReactiveVariable<NumericalControlSpec>) = ControlBus(
            reactiveVariable(name),
            reactiveVariable(channels),
            spec
        )

        fun create(rate: Rate, name: String, channels: Int, spec: NumericalControlSpec? = null): BusObject = when (rate) {
            Audio -> audio(name, channels)
            Control -> control(name, channels, reactiveVariable(spec!!))
        }

        val DATA_FORMAT = DataFormat("bus")
    }
}