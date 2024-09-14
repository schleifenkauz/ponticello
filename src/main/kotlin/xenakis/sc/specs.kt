package xenakis.sc

import hextant.codegen.Choice
import hextant.codegen.Component
import hextant.codegen.Compound
import hextant.codegen.UseEditor
import hextant.context.Context
import hextant.core.editor.ColorEditor
import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import reaktive.value.reactiveVariable
import xenakis.impl.ColorSerializer
import xenakis.impl.DoubleRange
import xenakis.model.*
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

fun ControlSpec.defaultControl(context: Context) = when (this) {
    is BufferControlSpec -> BufferControl(reactiveVariable(null))
    is BusControlSpec -> BusControl(reactiveVariable(context[BusRegistry].getDefault().createReference()))
    is NumericalControlSpec -> ConstantControl(reactiveVariable(defaultValue.get()))
    is GroupControlSpec -> GroupControl(reactiveVariable(context[GroupRegistry].getDefault().createReference()))
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
    val accuracy get() = accuracy(step.get())

    constructor(default: Double, min: Double, max: Double, warp: Warp, step: Double, associatedColor: Color) : this(
        DoubleLiteral(default), DoubleLiteral(min), DoubleLiteral(max), warp, DoubleLiteral(step), associatedColor
    )

    override val type: ParameterType
        get() = ParameterType.Numerical

    override val code: String
        get() = "kr(${defaultValue.text}, spec: [${min.text}, ${max.text}, $warp, ${step.text}])"

    val range: DoubleRange get() = min.get()..max.get()

    override fun toString(): String =
        "default: ${defaultValue.text}, range: ${min.text}..${max.text}, warp: $warp, step: ${step.text}"

    companion object {
        val DEFAULT = NumericalControlSpec(0.0, 0.0, 1.0, Warp.Linear, 0.1, Color.WHITE)
    }
}

@Compound(serializable = true)
@Serializable
class BusControlSpec : ControlSpec {
    override val type: ParameterType
        get() = ParameterType.Bus

    override val code: String
        get() = "kr"

    override fun equals(other: Any?): Boolean = other is BusControlSpec

    override fun hashCode(): Int = -2

    override fun toString(): String = "bus"
}

@Serializable
@Compound(serializable = true)
class BufferControlSpec(val isPlayBufSource: Boolean = true) : ControlSpec {
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
        get() = ParameterType.Unknown

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