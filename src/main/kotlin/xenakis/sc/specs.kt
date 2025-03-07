package xenakis.sc

import fxutils.controls.SliderBar
import hextant.codegen.Choice
import hextant.codegen.Component
import hextant.codegen.Compound
import hextant.codegen.UseEditor
import hextant.context.Context
import hextant.core.editor.ColorEditor
import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import reaktive.value.reactiveVariable
import xenakis.impl.*
import xenakis.model.flow.FlowType
import xenakis.model.registry.BusRegistry
import xenakis.model.registry.GroupRegistry
import xenakis.model.registry.ObjectReference
import xenakis.model.score.BufferControl
import xenakis.model.score.BusControl
import xenakis.model.score.ConstantControl
import xenakis.model.score.GroupControl
import xenakis.sc.editor.ControlSpecEditor
import xenakis.sc.editor.SimpleIntegerEditor

enum class ParameterType {
    Bus, Buffer, Numerical, Group;

    override fun toString(): String = when (this) {
        Bus -> "bus"
        Buffer -> "buf"
        Numerical -> "num"
        Group -> "group"
    }
}

@Serializable
@UseEditor(ControlSpecEditor::class)
sealed interface ControlSpec {
    val type: ParameterType

    val code: String
}

fun ControlSpec.defaultControl(context: Context, defaultBus: ObjectReference?) = when (this) {
    is BufferControlSpec -> BufferControl(reactiveVariable(null))
    is BusControlSpec -> {
        val bus = defaultBus ?: context[BusRegistry].getDefault().reference()
        BusControl(reactiveVariable(bus))
    }

    is NumericalControlSpec -> ConstantControl(reactiveVariable(defaultValue.get()))
    is GroupControlSpec -> GroupControl(reactiveVariable(context[GroupRegistry].getDefault().reference()))
}

@Serializable
@Compound(serializable = true)
data class NumericalControlSpec(
    val defaultValue: DecimalLiteral,
    val min: DecimalLiteral,
    val max: DecimalLiteral,
    val warp: Warp,
    val step: DecimalLiteral,
    @Serializable(with = ColorSerializer::class)
    @Component(ColorEditor::class)
    val associatedColor: Color = Color.WHITE
) : ControlSpec {
    val precision get() = step.get().precision

    constructor(
        default: Double, min: Double, max: Double, step: Decimal,
        warp: Warp = Warp.Linear, associatedColor: Color = Color.WHITE
    ) : this(
        default.withPrecision(step.precision),
        min.withPrecision(step.precision), max.withPrecision(step.precision),
        step, warp, associatedColor
    )

    constructor(default: Decimal, min: Decimal, max: Decimal, step: Decimal, warp: Warp, associatedColor: Color) : this(
        DecimalLiteral(default), DecimalLiteral(min), DecimalLiteral(max), warp, DecimalLiteral(step), associatedColor
    )

    override val type: ParameterType
        get() = ParameterType.Numerical

    override val code: String
        get() = "kr(${defaultValue.get()}, spec: [${min.get()}, ${max.get()}, $warp, ${step.get()}])"

    val range: DecimalRange get() = min.get()..max.get()

    override fun toString(): String =
        "default: ${defaultValue.text}, range: ${min.text}..${max.text}, warp: $warp, step: ${step.text}"

    fun converter(): SliderBar.Converter<Decimal> = Converter(this)

    private class Converter(spec: NumericalControlSpec) : SliderBar.Converter<Decimal> {
        private val defaultTransformation = SpecTransformation(spec, 0.0..1.0)

        private val precision = spec.precision

        override fun fromDouble(value: Double): Decimal = Decimal(defaultTransformation.unmap(value), precision)

        override fun fromLiteral(value: String): Decimal? = value.parseDecimal()?.withPrecision(precision)

        override fun toDouble(value: Decimal): Double = defaultTransformation.map(value.toDouble())

        override fun toString(value: Decimal): String = value.toCanonicalString()
    }

    companion object {
        val DEFAULT = NumericalControlSpec(zero, zero, one, 0.01.toDecimal(), Warp.Linear, Color.WHITE)
    }
}

@Compound(serializable = true)
@Serializable
class BusControlSpec(
    val rate: Rate,
    @Component(editor = SimpleIntegerEditor::class) val channels: Int,
    val flow: FlowType
) : ControlSpec {
    override val type: ParameterType
        get() = ParameterType.Bus

    override val code: String
        get() = rate.toString()

    override fun equals(other: Any?): Boolean = other is BusControlSpec

    override fun hashCode(): Int = -2

    override fun toString(): String = "bus"

}

@Serializable
@Compound(serializable = true)
class BufferControlSpec : ControlSpec {
    var isPlayBufSource: Boolean = true

    override val type: ParameterType
        get() = ParameterType.Buffer

    override val code: String
        get() = "kr"

    override fun equals(other: Any?): Boolean = other is BufferControlSpec

    override fun hashCode(): Int = -1

    override fun toString(): String = "buf"
}

@Serializable
class GroupControlSpec : ControlSpec {
    override val type: ParameterType
        get() = ParameterType.Group

    override val code: String
        get() = throw UnsupportedOperationException("Group control has no code")

    override fun equals(other: Any?): Boolean = other is GroupControlSpec

    override fun hashCode(): Int = -1

    override fun toString(): String = "buf"
}

fun NumericalControlSpec.mapOnto(targetRange: DoubleRange) = SpecTransformation(this, targetRange)

@Choice(defaultValue = "Rate.Audio")
enum class Rate {
    Audio, Control;

    override fun toString(): String = when (this) {
        Audio -> "ar"
        Control -> "kr"
    }
}