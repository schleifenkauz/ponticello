package ponticello.sc

import fxutils.controls.SliderBar
import hextant.codegen.Choice
import hextant.codegen.Component
import hextant.codegen.Compound
import hextant.codegen.UseEditor
import javafx.scene.paint.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.*
import ponticello.model.registry.ObjectReference
import ponticello.model.score.controls.*
import ponticello.sc.editor.*
import ponticello.ui.controls.*
import reaktive.value.ReactiveVariable
import reaktive.value.reactiveVariable

@Serializable
enum class ParameterType {
    Bus, Buffer, Numerical, Expr, BufferPosition, Trig, AttackRelease;

    override fun toString(): String = when (this) {
        Bus -> "bus"
        Buffer -> "buf"
        Numerical -> "num"
        Expr -> "expr"
        BufferPosition -> "buf-pos"
        Trig -> "trig"
        AttackRelease -> "attack-release"
    }

    companion object {
        val regularTypes = listOf(Numerical, Bus, Buffer, Expr, BufferPosition)
    }
}

fun ParameterType.defaultControlSpec(): ControlSpec = when (this) {
    ParameterType.Bus -> BusControlSpec(Rate.Audio, 2)
    ParameterType.Buffer -> BufferControlSpec(channels = 2)
    ParameterType.Numerical -> NumericalControlSpec.DEFAULT
    ParameterType.Expr -> ExprControlSpec()
    ParameterType.Trig -> NumericalControlSpec.TRIGGER
    ParameterType.BufferPosition -> BufferPositionControlSpec()
    ParameterType.AttackRelease -> AttackReleaseControlSpec()
}

@Serializable
@UseEditor(ControlSpecEditor::class)
sealed interface ControlSpec {
    val type: ParameterType

    val code: String

    val defaultValueExpr: String? get() = null

    val inlineDisplay: Boolean get() = false
}

fun ControlSpec.defaultControl() = when (this) {
    is BufferControlSpec -> BufferControl(reactiveVariable(ObjectReference.none()))
    is BusControlSpec -> BusControl(reactiveVariable(ObjectReference.none()))
    is NumericalControlSpec -> ValueControl(reactiveVariable(defaultValue.get()))
    is ExprControlSpec -> ExprControl.create()
    is BufferPositionControlSpec -> ValueControl(reactiveVariable(zero))
    is AttackReleaseControlSpec -> AttackReleaseControl.createDefault()
}

fun ControlSpec.defaultControlType() = when (this) {
    is AttackReleaseControlSpec -> AttackReleaseControlType
    is BufferControlSpec -> BufferControlType
    is BufferPositionControlSpec -> ValueControlType
    is BusControlSpec -> BusControlType
    is ExprControlSpec -> ExprControlType
    is NumericalControlSpec -> ValueControlType
}

fun ControlSpec.setDefaultExpr(expander: ScExprExpander) {
    when (this) {
        is BufferControlSpec -> {
            val bufferSelector = BufferSelector()
            bufferSelector.selectInitial(ObjectReference.none())
            expander.setInitialContent(bufferSelector)
        }

        is BusControlSpec -> {
            val busSelector = BusSelector()
            busSelector.selectInitial(ObjectReference.none())
            expander.setInitialContent(busSelector)
        }

        is NumericalControlSpec -> expander.setInitialText(defaultValue.text)
        is ExprControlSpec -> expander.setInitialText("")
        is BufferPositionControlSpec -> expander.setInitialText("0")
        is AttackReleaseControlSpec -> expander.setInitialText("0")
    }
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
    @Serializable(with = ColorSerializer::class) @Component(SimpleColorEditor::class)
    val associatedColor: Color = Color.WHITE,
    @Component(SimpleBooleanEditor::class) override val inlineDisplay: Boolean = false,
    @Component(SimpleBooleanEditor::class) val attackRelease: Boolean = false,
    @Component(SimpleBooleanEditor::class) val allocateBus: Boolean = false,
    @Component(SimpleBooleanEditor::class) val isStretch: Boolean = false
) : ControlSpec {
    val precision get() = step.get().precision

    @Transient
    var origin: ControlSpec? = null

    constructor(
        default: Double, min: Double, max: Double, step: Decimal, warp: Warp = Warp.Linear,
        lag: Double = 0.0, associatedColor: Color = Color.WHITE,
    ) : this(
        default.withPrecision(step.precision),
        min.withPrecision(step.precision), max.withPrecision(step.precision),
        step, warp, lag.withPrecision(step.precision), associatedColor
    )

    constructor(
        default: Decimal, min: Decimal, max: Decimal,
        step: Decimal, warp: Warp = Warp.Linear, lag: Decimal = zero,
        associatedColor: Color = Color.WHITE,
        inlineDisplay: Boolean = false, attackRelease: Boolean = false, allocateBus: Boolean = false
    ) : this(
        DecimalLiteral(default), DecimalLiteral(min), DecimalLiteral(max), warp,
        DecimalLiteral(step), DecimalLiteral(lag), associatedColor,
        inlineDisplay, attackRelease, allocateBus
    )

    override val type: ParameterType
        get() = ParameterType.Numerical

    override val code: String
        get() = "kr(${defaultValue.text}, lag: ${lag.text}, spec: [${min.text}, ${max.text}, ${warp.code}, ${step.text}])"

    override val defaultValueExpr: String
        get() = defaultValue.text

    val range: DecimalRange get() = min.get()..max.get()

    override fun toString(): String =
        "default: ${defaultValue.text}, range: ${min.text}..${max.text}, warp: $warp, step: ${step.text}"

    fun converter(
        unit: String = "", updateRange: (min: Decimal, max: Decimal) -> Boolean = { _, _ -> false }
    ): SliderBar.Converter<Decimal> = Converter(this, unit, updateRange)

    private class Converter(
        private val spec: NumericalControlSpec,
        private val unit: String,
        private val updateRange: (min: Decimal, max: Decimal) -> Boolean
    ) : SliderBar.Converter<Decimal> {
        private val defaultTransformation = SpecTransformation(spec, 0.0..1.0)

        private val precision = spec.precision

        override fun fromDouble(value: Double): Decimal = Decimal(defaultTransformation.unmap(value), precision)

        override fun fromLiteral(value: String): Decimal? {
            val value = value.removeSuffix(unit).parseDecimal()?.withPrecision(precision) ?: return null
            return if (value !in spec.range) {
                val newMin = minOf(spec.min.get(), value)
                val newMax = maxOf(spec.max.get(), value)
                if (updateRange(newMin, newMax)) value else null
            } else value
        }

        override fun toDouble(value: Decimal): Double = defaultTransformation.map(value.toDouble())

        override fun toString(value: Decimal): String = value.toCanonicalString() + unit
    }

    companion object {
        val DEFAULT = NumericalControlSpec(
            zero, zero, one, 0.01.toDecimal(),
            Warp.Linear, AttackReleaseControl.DEFAULT
        )

        val GATE = NumericalControlSpec(one, zero, one, one, Warp.Linear, zero)
        val TRIGGER = NumericalControlSpec(zero, zero, one, one, Warp.Linear, zero)

        val LEVEL = NumericalControlSpec(
            one, zero, one, 0.01.toDecimal(),
            Warp.Linear, AttackReleaseControl.DEFAULT
        )

        val BALANCE = NumericalControlSpec(
            0.0, -100.0, 100.0, 1.0.withPrecision(0), lag = 0.02, associatedColor = Color.GREEN
        )

        val VELOCITY = NumericalControlSpec(64.0, 0.0, 127.0, one)
        val PITCH = NumericalControlSpec(60.0, 0.0, 127.0, one)
        val CHANNEL = NumericalControlSpec(0.0, 0.0, 127.0, one)

        val DURATION = NumericalControlSpec(0.0, 0.0, Double.POSITIVE_INFINITY, 0.01.toDecimal())
        val AUTO_RELEASE = NumericalControlSpec(1.0, 0.0, 1.0, 1.toDecimal())
    }
}

@Compound
@Serializable
@SerialName("bus")
data class BusControlSpec(
    val rate: Rate,
    @Component(editor = SimpleIntegerEditor::class) val channels: Int,
    @Component(SimpleBooleanEditor::class) override val inlineDisplay: Boolean = true,
) : ControlSpec {
    override val type: ParameterType
        get() = ParameterType.Bus

    override val code: String
        get() = "kr.${rate.name.lowercase()}Bus($channels)"

    override fun toString(): String = "bus: [$channels x $rate]"
}

@Serializable
@Compound
@SerialName("buffer")
data class BufferControlSpec(
    @Component(editor = SimpleIntegerEditor::class) val channels: Int,
    @Component(SimpleBooleanEditor::class) override val inlineDisplay: Boolean = false,
    @Component(SimpleBooleanEditor::class) val displaySpectrogram: Boolean = true
) : ControlSpec {
    override val type: ParameterType
        get() = ParameterType.Buffer

    override val code: String
        get() = "kr"

    override fun toString(): String = "buf [$channels]"
}

@Serializable
@Compound
@SerialName("expr")
class ExprControlSpec : ControlSpec {
    override val code: String
        get() = throw UnsupportedOperationException("ObjectControlSpec cannot be used in SynthDefs.")

    override val type: ParameterType
        get() = ParameterType.Expr

    override fun equals(other: Any?): Boolean = other is ExprControlSpec

    override fun hashCode(): Int = javaClass.hashCode()
}

@Serializable
@SerialName("BufPos")
@Compound
data class BufferPositionControlSpec(
    @Component(SimpleBooleanEditor::class) override val inlineDisplay: Boolean = false,
) : ControlSpec {
    override val type: ParameterType
        get() = ParameterType.Numerical

    override val code: String
        get() = "kr(0)"
}

@Serializable
@SerialName("AttackRelease")
class AttackReleaseControlSpec : ControlSpec {
    val maxDuration: ReactiveVariable<Decimal?> = reactiveVariable(null)
    override val type: ParameterType
        get() = ParameterType.AttackRelease
    override val code: String
        get() = "<attack-release>"
}

fun NumericalControlSpec.mapOnto(min: Double, max: Double) = SpecTransformation(this, min..max)

@Choice(initialValue = "Rate.Audio")
enum class Rate {
    Audio, Control;

    override fun toString(): String = when (this) {
        Audio -> "ar"
        Control -> "kr"
    }
}