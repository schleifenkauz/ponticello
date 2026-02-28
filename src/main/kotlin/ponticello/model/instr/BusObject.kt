package ponticello.model.instr

import fxutils.drag.TypedDataFormat
import fxutils.undo.UndoManager
import fxutils.undo.VariableEdit
import hextant.context.Context
import javafx.application.Platform
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.Decimal
import ponticello.impl.zero
import ponticello.model.obj.AbstractSuperColliderObject
import ponticello.model.obj.BusReference
import ponticello.model.obj.withName
import ponticello.model.server.BusRegistry
import ponticello.sc.*
import ponticello.sc.Rate.Audio
import ponticello.sc.Rate.Control
import ponticello.sc.client.ScWriter
import ponticello.sc.client.run
import reaktive.Observer
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.binding.map
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
sealed class BusObject : AbstractSuperColliderObject() {
    abstract val rate: Rate
    abstract val channels: ReactiveVariable<Int>
    abstract val busType: Type

    override fun superColliderName(objectName: String) = when (busType) {
        Type.Input -> "s.inputBus"
        Type.Output -> "s.outputBus"
        Type.Regular -> "~bus_${objectName}"
    }

    @Transient
    private lateinit var observer: Observer

    override val canRename: Boolean
        get() = busType == Type.Regular

    override val canDelete: Boolean
        get() = busType == Type.Regular

    override val registry: BusRegistry
        get() = context[BusRegistry]

    override fun initialize(context: Context) {
        super.initialize(context)
        if (busType == Type.Regular) {
            observer = channels.observe { _ ->
                sync()
            }
        }
    }

    fun matches(spec: ControlSpec?): Boolean =
        spec is BusControlSpec && rate == spec.rate && channels.now == spec.channels

    enum class Type {
        Regular, Input, Output;
    }

    @SerialName("AudioBus")
    @Serializable
    class AudioBus(
        override val channels: ReactiveVariable<Int>,
        override val busType: Type = Type.Regular,
    ) : BusObject() {
        @SerialName("name")
        override var _name: ReactiveVariable<String>? = null

        override val rate: Rate get() = Audio

        fun getLevel(channel: Int) = registry.getLevel(this, channel)

        override fun ScWriter.createObject() {
            when (busType) {
                Type.Input -> {}
                Type.Output -> {}
                Type.Regular -> {
                    +"$superColliderName = Bus.audio(s, ${channels.now})"
                }
            }
            for ((ch, id) in registry.registerLevelSends(this@AudioBus).withIndex()) {
                +"~level_send_${name.now}_$ch = Synth(\\send_level, [bus: $superColliderName.index + $ch, id: $id])"
            }
        }

        override fun onRename(oldName: String, newName: String) {
            super.onRename(oldName, newName)
            client.run {
                for (i in 0 until channels.now) {
                    +"~level_send_${newName}_$i = ~level_send_$oldName"
                    +"~level_send_${oldName}_$i = nil"
                }
            }
        }

        override fun ScWriter.freeObject() {
            registry.clearBusChannels(this@AudioBus)
            +"$superColliderName.release"
            +"$superColliderName = nil"
            for (i in 0 until channels.now) {
                +"~level_send_${name.now}_$i.release"
                +"~level_send_${name.now}_$i = nil"
            }
        }
    }

    @Serializable
    @SerialName("ControlBus")
    class ControlBus(
        override val channels: ReactiveVariable<Int>,
        private val _spec: ReactiveVariable<NumericalControlSpec?> = reactiveVariable(null),
    ) : BusObject() {
        @SerialName("name")
        override var _name: ReactiveVariable<String>? = null

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
            reactiveVariable(2),
            busType = Type.Input,
        ).withName("input")

        val output = AudioBus(
            reactiveVariable(2),
            busType = Type.Output,
        ).withName("output")

        fun audio(name: String, channels: Int = 2) = AudioBus(
            reactiveVariable(channels),
            Type.Regular
        ).withName(name)

        fun control(name: String, channels: Int = 1, spec: NumericalControlSpec? = null) = ControlBus(
            reactiveVariable(channels),
            reactiveVariable(spec)
        ).withName(name)

        fun create(rate: Rate, name: String, channels: Int): BusObject =
            when (rate) {
                Audio -> audio(name, channels)
                Control -> control(name, channels)
            }

        val DATA_FORMAT = TypedDataFormat<BusReference>("ponticello/bus")
    }
}