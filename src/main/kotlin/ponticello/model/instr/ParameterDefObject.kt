package ponticello.model.instr

import hextant.context.Context
import javafx.scene.input.DataFormat
import javafx.scene.paint.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.copy
import ponticello.impl.one
import ponticello.impl.toDecimal
import ponticello.model.obj.AbstractRenamableObject
import ponticello.model.obj.withName
import ponticello.sc.*
import ponticello.sc.editor.ControlSpecEditor
import reaktive.Observer
import reaktive.and
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
class ParameterDefObject(val spec: ReactiveVariable<ControlSpec>) : AbstractRenamableObject() {
    @SerialName("name")
    override var _name: ReactiveVariable<String>? = null

    @Transient
    private lateinit var observer: Observer

    @Transient
    lateinit var specEditor: ControlSpecEditor
        private set

    var isImmutable = false
        private set

    override fun initialize(context: Context) {
        super.initialize(context)
        specEditor = makeControlSpecEditor()
        syncSpecWithEditor()
    }

    private fun makeControlSpecEditor(): ControlSpecEditor {
        val editor = ControlSpecEditor()
        editor.setResult(spec.now)
        editor.initialize(context)
        specEditor = editor
        return editor
    }

    private fun syncSpecWithEditor() {
        observer =
            spec.observe { _, _, spec ->
                if (specEditor.result.now != spec) specEditor.setResult(spec)
            } and specEditor.result.observe { _, _, new ->
                if (new != spec.now) spec.now = new
            }
    }

    private fun immutable() = also { isImmutable = true }

    override fun canRenameTo(newName: String): Boolean = !isImmutable

    fun defaultControl() = spec.now.defaultControl()

    override fun toString(): String = "${name.now}: ${spec.now}"

    fun simpleString(): String {
        val type = spec.now.type.toString()
        return "${name.now} ($type)"
    }

    override fun copy() = ParameterDefObject(spec.copy())

    companion object {
        val DATA_FORMAT: DataFormat = DataFormat("ponticello/parameter-def")

        val FREQ = ParameterDefObject(
            "freq",
            NumericalControlSpec(440.0, 20.0, 20000.0, 1.0.toDecimal(), 0.02, Warp.Exponential, Color.BLACK)
        )

        val MIDINOTE = ParameterDefObject(
            "midinote",
            NumericalControlSpec(60.0, 0.0, 127.0, step = one, warp = Warp.Linear)
        )

        val AMP = ParameterDefObject(
            "amp",
            NumericalControlSpec(0.1, 0.0, 1.0, 0.01.toDecimal(), 0.02, Warp.Linear, Color.ORANGE)
        )
        val PAN = ParameterDefObject(
            "pan",
            NumericalControlSpec(0.0, -1.0, 1.0, 0.1.toDecimal(), 0.02, Warp.Linear, Color.BLUE)
        )

        val BUF = ParameterDefObject("buf", BufferControlSpec(2))
        val OUT = ParameterDefObject("out", BusControlSpec(Rate.Audio, 2))
        val IN = ParameterDefObject("in", BusControlSpec(Rate.Audio, 2))
        val BUS = ParameterDefObject("bus", BusControlSpec(Rate.Control, 2))

        val VELOCITY = ParameterDefObject("velocity", NumericalControlSpec.VELOCITY).immutable()
        val CHANNEL = ParameterDefObject("channel", NumericalControlSpec.CHANNEL).immutable()

        val LEVEL = ParameterDefObject("level", NumericalControlSpec.LEVEL).immutable()
        val DURATION = ParameterDefObject("duration", NumericalControlSpec.DURATION)
        val AUTO_RELEASE = ParameterDefObject("auto_release", NumericalControlSpec.AUTO_RELEASE)

        val ATTACK_RELEASE = ParameterDefObject("attack-release", AttackReleaseControlSpec()).immutable()

        val defaults = listOf(FREQ, AMP, PAN)

        operator fun invoke(name: String, spec: ControlSpec) = ParameterDefObject(reactiveVariable(spec)).withName(name)
    }
}