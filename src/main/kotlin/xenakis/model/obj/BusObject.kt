package xenakis.model.obj

import hextant.context.Context
import javafx.scene.input.DataFormat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.Observer
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
    abstract val busType: Type

    override val superColliderName
        get() = when (busType) {
            Type.Input -> "s.inputBus"
            Type.Output -> "s.outputBus"
            Type.Regular -> "~bus_${name.now}"
        }

    @Transient
    private lateinit var observer: Observer

    override val canRename: Boolean
        get() = busType == Type.Regular

    override val canDelete: Boolean
        get() = busType == Type.Regular

    override val registry: ObjectRegistry<*>?
        get() = context[BusRegistry]

    override fun canRenameTo(newName: String): Boolean =
        name.now.startsWith("global_") == newName.startsWith("global_") &&
                !context[XenakisLauncher.currentProject].busses.has(newName)

    override fun initialize(context: Context) {
        super.initialize(context)
        if (busType == Type.Regular) {
            observer = channels.observe { _ -> redefine() }
        }
    }

    enum class Type {
        Regular, Input, Output;
    }

    @SerialName("AudioBus")
    @Serializable
    class AudioBus(
        @SerialName("name") override val mutableName: ReactiveVariable<String>,
        override val channels: ReactiveVariable<Int>,
        override val busType: Type
    ) : BusObject() {
        override val rate: Rate get() = Audio

        override fun ScWriter.createObject() {
            when (busType) {
                Type.Input -> {}
                Type.Output -> {}
                Type.Regular -> {
                    +"$superColliderName = Bus.audio(s, ${channels.now})"
                }
            }
        }
    }

    @Serializable
    @SerialName("ControlBus")
    class ControlBus(
        @SerialName("name") override val mutableName: ReactiveVariable<String>,
        override val channels: ReactiveVariable<Int>,
        val spec: ReactiveVariable<NumericalControlSpec>,
    ) : BusObject() {
        override val rate: Rate get() = Control
        override val busType: Type
            get() = Type.Regular

        @Transient
        val defaultValue = spec.map { s -> s.defaultValue.get() }

        @Transient
        private lateinit var defaultValueObserver: Observer

        override fun initialize(context: Context) {
            super.initialize(context)
            defaultValueObserver = defaultValue.observe { _ ->
                client.run {
                    setDefaultValue(skipIfZero = false)
                }
            }
        }

        override fun ScWriter.createObject() {
            +"$superColliderName = Bus.control(s, ${channels.now})"
            setDefaultValue(skipIfZero = true)
        }

        private fun ScWriter.setDefaultValue(skipIfZero: Boolean) {
            val value = defaultValue.now
            if (skipIfZero && value == zero) return
            val valueList = List(channels.now) { value }.joinToString()
            +"$superColliderName.set($valueList)"
        }
    }

    companion object {
        val input = AudioBus(
            reactiveVariable("input"),
            reactiveVariable(2),
            busType = Type.Input,
        )

        val output = AudioBus(
            reactiveVariable("output"),
            reactiveVariable(2),
            busType = Type.Output,
        )

        fun audio(name: String, channels: Int = 2) = AudioBus(
            reactiveVariable(name),
            reactiveVariable(channels),
            Type.Regular
        )

        fun control(name: String, channels: Int = 1, spec: NumericalControlSpec) = ControlBus(
            reactiveVariable(name),
            reactiveVariable(channels),
            reactiveVariable(spec)
        )

        fun create(rate: Rate, name: String, channels: Int, spec: NumericalControlSpec? = null): BusObject =
            when (rate) {
                Audio -> audio(name, channels)
                Control -> control(name, channels, spec!!)
            }

        val DATA_FORMAT = DataFormat("bus")
    }
}