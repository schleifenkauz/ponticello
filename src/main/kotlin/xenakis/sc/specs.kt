package xenakis.sc

import fxutils.controls.SliderBar
import hextant.codegen.Choice
import hextant.codegen.Component
import hextant.codegen.Compound
import hextant.codegen.UseEditor
import javafx.scene.paint.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.reactiveVariable
import xenakis.impl.*
import xenakis.model.obj.BusReference
import xenakis.model.registry.ObjectReference
import xenakis.model.score.controls.AttackReleaseControl
import xenakis.model.score.controls.BufferControl
import xenakis.model.score.controls.BusControl
import xenakis.model.score.controls.ValueControl
import xenakis.sc.editor.ControlSpecEditor
import xenakis.sc.editor.SimpleColorEditor
import xenakis.sc.editor.SimpleIntegerEditor

@Serializable
enum class ParameterType {
    Bus, Buffer, Numerical, BufferPosition, AttackRelease;

    override fun toString(): String = when (this) {
        Bus -> "bus"
        Buffer -> "buf"
        Numerical -> "num"
        BufferPosition -> "buf-pos"
        AttackRelease -> "attack-release"
    }

    companion object {
        val regularTypes = listOf(Numerical, Bus, Buffer, BufferPosition)
    }
}

fun ParameterType.defaultControlSpec(): ControlSpec = when (this) {
    ParameterType.Bus -> BusControlSpec(Rate.Audio, 2)
    ParameterType.Buffer -> BufferControlSpec(channels = 2)
    ParameterType.Numerical -> NumericalControlSpec.DEFAULT
    ParameterType.BufferPosition -> BufferPositionControlSpec()
    ParameterType.AttackRelease -> AttackReleaseControlSpec()
}

@Serializable
@UseEditor(ControlSpecEditor::class)
sealed interface ControlSpec {
    val type: ParameterType

    val code: String

    val defaultValueExpr: String? get() = null
}

fun ControlSpec.defaultControl(defaultBus: BusReference?) = when (this) {
    is BufferControlSpec -> BufferControl(reactiveVariable(ObjectReference.none()))
    is BusControlSpec -> {
        val bus = defaultBus ?: ObjectReference.none()
        BusControl(reactiveVariable(bus))
    }

    is NumericalControlSpec -> ValueControl(reactiveVariable(defaultValue.get()))
    is BufferPositionControlSpec -> ValueControl(reactiveVariable(zero))
    is AttackReleaseControlSpec -> AttackReleaseControl.createDefault()
}

@Serializable
@Compound
@SerialName("numerical")
data class NumericalControlSpec(
    val defaultValue: DecimalLiteral,
    val min: DecimalLiteral,
    val max: DecimalLiteral,
    val warp: Warp,
    val step: DecimalLiteral,
    val lag: DecimalLiteral = DecimalLiteral(AttackReleaseControl.DEFAULT),
    @Serializable(with = ColorSerializer::class)
    @Component(SimpleColorEditor::class)
    val associatedColor: Color = Color.WHITE,
) : ControlSpec {
    val precision get() = step.get().precision

    constructor(
        default: Double, min: Double, max: Double, step: Decimal, lag: Double,
        warp: Warp = Warp.Linear, associatedColor: Color = Color.WHITE,
    ) : this(
        default.withPrecision(step.precision),
        min.withPrecision(step.precision), max.withPrecision(step.precision),
        step, lag.withPrecision(step.precision), warp, associatedColor
    )

    constructor(
        default: Decimal, min: Decimal, max: Decimal, step: Decimal, lag: Decimal,
        warp: Warp, associatedColor: Color = Color.WHITE,
    ) : this(
        DecimalLiteral(default), DecimalLiteral(min), DecimalLiteral(max), warp,
        DecimalLiteral(step), DecimalLiteral(lag), associatedColor
    )

    override val type: ParameterType
        get() = ParameterType.Numerical

    override val code: String
        get() = "kr(${defaultValue.text}, lag: ${lag.text}, spec: [${min.text}, ${max.text}, $warp, ${step.text}])"

    override val defaultValueExpr: String
        get() = defaultValue.text

    val range: DecimalRange get() = min.get()..max.get()

    override fun toString(): String =
        "default: ${defaultValue.text}, range: ${min.text}..${max.text}, warp: $warp, step: ${step.text}"

    fun converter(unit: String = ""): SliderBar.Converter<Decimal> = Converter(this, unit)

    private class Converter(spec: NumericalControlSpec, private val unit: String) : SliderBar.Converter<Decimal> {
        private val defaultTransformation = SpecTransformation(spec, 0.0..1.0)

        private val precision = spec.precision

        override fun fromDouble(value: Double): Decimal = Decimal(defaultTransformation.unmap(value), precision)

        override fun fromLiteral(value: String): Decimal? =
            value.removeSuffix(unit).parseDecimal()?.withPrecision(precision)

        override fun toDouble(value: Decimal): Double = defaultTransformation.map(value.toDouble())

        override fun toString(value: Decimal): String = value.toCanonicalString() + unit
    }

    companion object {
        val DEFAULT = NumericalControlSpec(
            zero, zero, one, 0.01.toDecimal(),
            AttackReleaseControl.DEFAULT, Warp.Linear, Color.WHITE
        )

        val LEVEL = NumericalControlSpec(
            one, zero, one, 0.01.toDecimal(),
            AttackReleaseControl.DEFAULT, Warp.Linear, Color.WHITE
        )
    }
}

@Compound
@Serializable
@SerialName("bus")
data class BusControlSpec(
    val rate: Rate,
    @Component(editor = SimpleIntegerEditor::class) val channels: Int,
) : ControlSpec {
    override val type: ParameterType
        get() = ParameterType.Bus

    override val code: String
        get() = "kr"

    override fun toString(): String = "bus: [$channels x $rate]"
}

@Serializable
@Compound
@SerialName("buffer")
data class BufferControlSpec(@Component(editor = SimpleIntegerEditor::class) val channels: Int) : ControlSpec {
    var isPlayBufSource: Boolean = true

    override val type: ParameterType
        get() = ParameterType.Buffer

    override val code: String
        get() = "kr"

    override fun toString(): String = "buf [$channels]"
}

@Serializable
@SerialName("BufPos")
@Compound
class BufferPositionControlSpec : ControlSpec {
    override val type: ParameterType
        get() = ParameterType.Numerical

    override val code: String
        get() = "kr(0)"
}

@Serializable
@SerialName("AttackRelease")
class AttackReleaseControlSpec : ControlSpec {
    val maxDuration: ReactiveVariable<Decimal> = reactiveVariable(one)
    override val type: ParameterType
        get() = ParameterType.AttackRelease
    override val code: String
        get() = "<attack-release>"
}

fun NumericalControlSpec.mapOnto(targetRange: DoubleRange) = SpecTransformation(this, targetRange)

@Choice(initialValue = "Rate.Audio")
enum class Rate {
    Audio, Control;

    override fun toString(): String = when (this) {
        Audio -> "ar"
        Control -> "kr"
    }
}