package xenakis.model

import hextant.core.editor.ViewManager
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put
import xenakis.impl.ScWriter
import xenakis.impl.getString
import xenakis.sc.ControlSpec
import xenakis.sc.SynthDef
import xenakis.ui.SynthObjectView
import xenakis.ui.XenakisController.Companion.currentProject
import xenakis.ui.format

class SynthObject(name: String, var synthDefName: String) : AbstractScoreObject(name) {
    override val type: String
        get() = "synth"

    override val viewManager: ViewManager<SynthObjectView> = ViewManager.createWeakViewManager()

    val synthDef: SynthDef
        get() = context[currentProject].synthDefs.get(synthDefName)

    override fun copy(): ScoreObject = SynthObject(name, synthDefName)

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

                    is SingleBusValueControl -> {
                        val bus = control.bus.variableName
                        +"${synthVar}.set(\\$param, $bus.getSynchronized)"
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

    override fun JsonObjectBuilder.saveToJson() {
        put("synthDef", synthDefName)
    }

    object Serializer : ScoreObject.Serializer {
        override val type: String
            get() = "synth"

        override fun JsonObject.createFromJson(name: String): ScoreObject {
            val synthDefName = getString("synthDef")!!
            return SynthObject(name, synthDefName)
        }
    }
}