package xenakis.sc

import hextant.context.Context
import reaktive.value.now
import xenakis.impl.Decimal
import xenakis.model.Settings
import xenakis.model.flow.ScoreObjectInfo
import xenakis.model.obj.BusObject
import xenakis.model.obj.SampleObject
import xenakis.model.obj.SynthDefObject
import xenakis.model.score.*
import xenakis.sc.client.ScWriter

fun ScWriter.writeSynthCode(
    name: String, def: SynthDefObject, controls: ParameterControls,
    context: Context, synthInfo: ScoreObjectInfo, duration: Decimal?
) {
    appendBlock("s.makeBundle(${context[Settings].serverLatency})") {
        val constantArguments = controls.controlMap.mapNotNull { (param, control) ->
            if (!def.hasParameter(param)) null
            else when (control) {
                is BufferControl -> param to (control.sample.now?.get<SampleObject>()?.superColliderName ?: "0")
                is BusControl -> param to control.bus.now.get<BusObject>().superColliderName
                is ConstantControl -> param to control.value.now.toString()
                is KnobControl -> param to control.get().toString()
                is EnvelopeControl -> param to control.envelope.points[0].value.toString()
                is SingleBusValueControl -> {
                    val bus = control.bus.now.get<BusObject>().superColliderName
                    param to "$bus.getSynchronous"
                }

                else -> null
            }
        }.joinToString { (param, value) -> "$param: $value" } + duration?.let { dur -> ", duration: $dur" }.orEmpty()
        val synthVar = "~synths['$name']"
        val synthDefName = def.name.now
        val (addAction, target) = synthInfo.placement ?: error("No placement specified for $this")
        +"$synthVar = Synth(\\$synthDefName, [$constantArguments], target: $target, addAction: $addAction)"
        +"$synthVar.register"
        for ((param, control) in controls.controlMap) {
            when (control) {
                is EnvelopeControl -> {
                    val envelopeCode = control.envelope.code()
                    val busName = "~auxil_${name}_${param}"
                    +"$busName  = Bus.control(s, 1)"
                    +"{ $envelopeCode }.play(s, $busName)"
                    +"${synthVar}.map(\\$param, $busName)"
                }

                is BusValueControl -> {
                    val bus = control.bus.now.get<BusObject>().superColliderName
                    +"${synthVar}.map(\\$param, $bus)"
                }

                /* TODO is this needed?
                                    is SingleBusValueControl -> {
                                        val bus = control.bus.now.get<BusObject>().superColliderName
                                        +"$bus.postln"
                                        +"${synthVar}.set(\\$param, $bus.value)"
                                    }
                */

                is CustomControl -> {
                    val code = controls.controlMap.entries
                        .associate { (name, control) -> "~ctrl_$name" to control.makeExpr() }
                    val expr = control.expr.editor.result.now
                        .transform<Identifier> { e -> code[e.text] ?: e }
                    val busName = "~auxil_${name}_${param}"
                    +"$busName = Bus.control(s, 1)"
                    if (duration != null) {
                        appendBlock("", endLine = false) {
                            +"Env.new(levels: [0, 0], times: [$duration]).kr(Done.freeSelf)"
                            expr.code(writer, context)
                        }
                    }
                    +".play(s, $busName)"
                    +"${synthVar}.map(\\$param, $busName)"
                }

                else -> {} //already handled in constantArguments
            }
        }
    }
}
