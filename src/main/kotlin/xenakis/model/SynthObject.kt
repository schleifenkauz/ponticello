package xenakis.model

import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import xenakis.impl.ScWriter
import xenakis.sc.ControlSpec
import xenakis.sc.Group
import xenakis.sc.SynthDef
import xenakis.ui.XenakisController.Companion.currentProject
import xenakis.ui.format

@Serializable
class SynthObject(
    override var name: String, var group: Group, var synthDefName: String,
    override var start: Double, override var duration: Double,
    override var y: Double, override var height: Double,
    override val controls: MutableList<ParameterControl>,
    override var muted: Boolean = false
) : ScoreObject() {
    val synthDef: SynthDef
        get() = context[currentProject].synthDefs.get(synthDefName)

    override val color: Color get() = synthDef.associatedColor

    override fun clone(newName: String): ScoreObject = SynthObject(
        newName, group, synthDefName,
        start, duration, y, height,
        controls.mapTo(mutableListOf()) { c -> c.clone() }
    )

    override fun getSpec(parameter: String): ControlSpec = synthDef.getParameter(parameter).spec

    override fun writeStartCode(writer: ScWriter, offset: Double) {
        writer.appendBlock("s.bind") {
            val synthVar = "~synth_${name}"
            +"$synthVar = Synth(\\${synthDefName})"
            for (control in controls) {
                val param = control.parameter
                when (control) {
                    is EnvelopeControl -> {
                        val env = control.envelope.code(offset, duration)
                        val busName = "~auxil_${name}_${param}"
                        +"$busName = Bus.control(s, 1)"
                        +"{ $env }.play(s, $busName)"
                        +"${synthVar}.map(\\$param, $busName)"
                    }

                    is KnobControl -> {
                        val value = control.value.format(2)
                        +"${synthVar}.set(\\$param, $value)"
                    }

                    is BusControl -> {
                        val bus = control.bus.variableName
                        +"${synthVar}.set(\\$param, $bus)"
                    }

                    is BusValueControl -> {
                        val bus = control.bus.variableName
                        +"${synthVar}.map(\\$param, $bus)"
                    }

                    is CustomControl -> {
                        val expr = control.expr
                        val busName = "~auxil_${name}_${param}"
                        +"$busName = Bus.control(s, 1)"
                        this.append("{ ")
                        expr.code(this)
                        +" }.play(s, $busName)"
                        +"${synthVar}.map(\\$param, $busName)"
                    }

                    is BufferControl -> {
                        val buf = control.buffer.variableName
                        +"${synthVar}.set(\\$param, $buf)"
                    }

                    is ConstantControl -> +"${synthVar}.set(\\$param, ${control.value})"
                }
            }
        }
        writer.appendLine(";")
    }

    override fun writeStopCode(writer: ScWriter) = with(writer) {
        val synthVar = "~synth_$name"
        +"$synthVar.free"
    }

}