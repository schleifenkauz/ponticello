package xenakis.model

import hextant.context.Context
import hextant.core.editor.ViewManager
import javafx.geometry.HorizontalDirection
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put
import xenakis.impl.ScWriter
import xenakis.impl.getString
import xenakis.sc.ControlSpec
import xenakis.sc.Group
import xenakis.sc.SynthDef
import xenakis.ui.SynthObjectView
import xenakis.ui.XenakisController.Companion.currentProject
import xenakis.ui.format

class SynthObject(
    name: String,
    var synthDefName: String,
    group: Group = Group.DEFAULT
) : AbstractScoreObject(name), GroupReference {
    override val type: String
        get() = "synth"

    override val viewManager: ViewManager<SynthObjectView> = ViewManager.createWeakViewManager()

    override var group: Group = group
        set(value) {
            if (field == value) return
            viewManager.notifyViews { changedGroup() }
            field = value
        }

    val synthDef: SynthDef
        get() = context[currentProject].synthDefs.get(synthDefName)

    override fun copy(): ScoreObject = SynthObject(name, synthDefName, group)

    override fun cut(position: Double, whichHalf: HorizontalDirection): ScoreObject =
        SynthObject(name, synthDefName, group)

    override fun getSpec(parameter: String): ControlSpec = synthDef.getParameter(parameter).spec

    override fun addToScore(score: Score, context: Context) {
        super.addToScore(score, context)
        context[GroupRegistry].groupReferences.addView(this)
    }

    override fun onRemove() {
        super.onRemove()
        context[GroupRegistry].groupReferences.removeView(this)
    }

    override fun writeStartCode(writer: ScWriter, offset: Double) {
        writer.appendBlock("s.bind") {
            val synthVar = "~synth_${name}"
            +"$synthVar = Synth(\\${synthDefName}, target: ${group.variableName})"
            for (control in controls) {
                val param = control.parameter
                when (control) {
                    is EnvelopeControl -> {
                        val env = control.envelope.code(offset)
                        val busName = "~auxil_${name}_${param}"
                        +"$busName = Bus.control(s, 1)"
                        +"{ $env }.play(s, $busName)"
                        +"${synthVar}.map(\\$param, $busName)"
                    }

                    is KnobControl -> {
                        val value = control.get().format(2)
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
        if (group != Group.DEFAULT) put("group", group.name)
    }

    object Serializer : ScoreObject.Serializer {
        override val type: String
            get() = "synth"

        override fun JsonObject.createFromJson(name: String): ScoreObject {
            val synthDefName = getString("synthDef")!!
            val groupName = getString("group") ?: "default"
            return SynthObject(name, synthDefName, Group(groupName))
        }
    }
}