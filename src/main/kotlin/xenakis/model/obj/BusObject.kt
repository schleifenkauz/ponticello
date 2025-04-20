package xenakis.model.obj

import hextant.context.Context
import hextant.undo.UndoManager
import hextant.undo.VariableEdit
import javafx.application.Platform
import javafx.scene.input.DataFormat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.Observer
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.binding.map
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.Decimal
import xenakis.impl.zero
import xenakis.model.registry.BusRegistry
import xenakis.model.registry.ObjectRegistry
import xenakis.sc.DecimalLiteral
import xenakis.sc.NumericalControlSpec
import xenakis.sc.Rate
import xenakis.sc.Rate.Audio
import xenakis.sc.Rate.Control
import xenakis.sc.client.ScWriter

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

    override fun initialize(context: Context) {
        super.initialize(context)
        if (busType == Type.Regular) {
            observer = channels.observe { _ ->
                sync()
            }
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
        override val busType: Type,
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
        private val _spec: ReactiveVariable<NumericalControlSpec?> = reactiveVariable(null),
    ) : BusObject() {
        override val rate: Rate get() = Control
        override val busType: Type
            get() = Type.Regular

        val spec: ReactiveValue<NumericalControlSpec?> get() = _spec

        @Transient
        val defaultValue = spec.map { s -> s?.defaultValue?.get() }

        fun setDefaultValue(value: Decimal) = Platform.runLater {
            _spec.now = spec.now?.copy(defaultValue = DecimalLiteral(value))
        }

        fun updateSpec(newSpec: NumericalControlSpec?) {
            val oldValue = spec.now
            _spec.now = newSpec
            context[UndoManager].record(VariableEdit(_spec, oldValue, newSpec, "Update numerical bus spec"))
        }

        @Transient
        private lateinit var defaultValueObserver: Observer

        override fun initialize(context: Context) {
            super.initialize(context)
            defaultValueObserver = defaultValue.observe { _, _, v ->
                client.run {
                    setDefaultValue(skipIfZero = false)
                }
            }
        }

        override fun ScWriter.createObject() {
            +"$superColliderName = Bus.control(s, ${channels.now})"
            setDefaultValue(skipIfZero = true)
        }

        fun ScWriter.setDefaultValue(skipIfZero: Boolean) {
            val value = defaultValue.now ?: return
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

        fun control(name: String, channels: Int = 1, spec: NumericalControlSpec? = null) = ControlBus(
            reactiveVariable(name),
            reactiveVariable(channels),
            reactiveVariable(spec)
        )

        fun create(rate: Rate, name: String, channels: Int): BusObject =
            when (rate) {
                Audio -> audio(name, channels)
                Control -> control(name, channels)
            }

        val DATA_FORMAT = DataFormat("bus")
    }
}