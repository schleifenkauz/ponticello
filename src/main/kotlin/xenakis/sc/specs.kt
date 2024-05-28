package xenakis.sc

import hextant.codegen.Component
import hextant.codegen.Compound
import hextant.codegen.UseEditor
import hextant.core.editor.ColorEditor
import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import xenakis.impl.ColorSerializer
import xenakis.impl.DoubleRange
import xenakis.sc.editor.ControlSpecEditor
import xenakis.ui.accuracy

enum class ParameterType {
    Bus, Buffer, Numerical, Unknown;

    override fun toString(): String = when (this) {
        Bus -> "bus"
        Buffer -> "buf"
        Numerical -> "num"
        Unknown -> "???"
    }
}

@Serializable
@UseEditor(ControlSpecEditor::class)
sealed interface ControlSpec {
    val type: ParameterType

    val code: String
}

@Serializable
@Compound(serializable = true)
data class NumericalControlSpec(
    val defaultValue: DoubleLiteral,
    val min: DoubleLiteral,
    val max: DoubleLiteral,
    val warp: Warp,
    val step: DoubleLiteral,
    @Serializable(with = ColorSerializer::class)
    @Component(ColorEditor::class)
    val associatedColor: Color = Color.WHITE
) : ControlSpec {
    val accuracy get() = accuracy(step.value)

    constructor(default: Double, min: Double, max: Double, warp: Warp, step: Double, associatedColor: Color) : this(
        DoubleLiteral(default), DoubleLiteral(min), DoubleLiteral(max), warp, DoubleLiteral(step), associatedColor
    )

    override val type: ParameterType
        get() = ParameterType.Numerical

    override val code: String
        get() = "kr(${defaultValue.text}, spec: [${min.text}, ${max.text}, $warp, ${step.text}])"

    val range: DoubleRange get() = min.value..max.value

    companion object {
        val DEFAULT = NumericalControlSpec(0.0, 0.0, 1.0, Warp.Linear, 0.1, Color.WHITE)
    }
}

@Compound(serializable = true)
@Serializable
data class BusControlSpec(val defaultValue: Bus) : ControlSpec {
    override val type: ParameterType
        get() = ParameterType.Bus

    override val code: String
        get() = "kr(${defaultValue.variableName})"
}

@Serializable
@Compound(serializable = true)
data class BufferControlSpec(val defaultValue: Buffer) : ControlSpec {
    override val type: ParameterType
        get() = ParameterType.Buffer

    override val code: String
        get() = "kr(${defaultValue.variableName})"
}

object ControlSpecUnspecified : ControlSpec {
    override val type: ParameterType
        get() = ParameterType.Unknown
    override val code: String
        get() = "[/*unspecified*/]"
}

fun NumericalControlSpec.mapOnto(targetRange: DoubleRange) = SpecTransformation(this, targetRange)
