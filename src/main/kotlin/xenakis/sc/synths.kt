package xenakis.sc

import hextant.context.Context
import reaktive.value.now
import xenakis.impl.Decimal
import xenakis.impl.Logger
import xenakis.model.flow.ScoreObjectInfo
import xenakis.model.obj.SynthDefObject
import xenakis.model.registry.ObjectReference
import xenakis.model.score.*
import xenakis.sc.client.ScWriter

private val SPECIAL_PARAMETERS = setOf("afterDuration")

fun ScWriter.writeSynthCode(
    def: SynthDefObject, context: Context,
    info: ScoreObjectInfo, duration: Decimal?,
    controls: ParameterControlList,
    controlMap: Map<String, ParameterControl> = controls.controlMap,
) {
    val constantArguments = controlMap.mapNotNull { (param, control) ->
        if (param !in SPECIAL_PARAMETERS && !def.hasParameter(param)) null
        else when (control) {
            is BufferControl -> param to (control.sample.now.get()?.superColliderName
                ?: return unresolvedReference("Sample", control.sample.now))

            is BusControl -> param to (control.bus.now.get()?.superColliderName
                ?: return unresolvedReference("Bus", control.bus.now))

            is ValueControl -> param to control.value.now.toString()
            is EnvelopeControl -> param to control.points.points[0].value.toString()
            is SingleBusValueControl -> {
                val bus = control.bus.now.get()?.superColliderName ?: return unresolvedReference("Bus", control.bus.now)
                param to "$bus.getSynchronous"
            }

            else -> null
        }
    }.joinToString { (param, value) -> "$param: $value" } + duration?.let { dur -> ", duration: $dur" }.orEmpty()
    val synthDefName = def.name.now
    val (addAction, target) = info.placement ?: error("No placement specified for $this")
    val name = info.name
    val synthVar = info.superColliderName
    +"$synthVar = Synth(\\$synthDefName, [$constantArguments], target: $target, addAction: $addAction)"
    +"$synthVar.register"
    for ((param, control) in controlMap) {
        when (control) {
            is EnvelopeControl -> {
                val spec = controls.get(param).spec.now as? NumericalControlSpec
                    ?: error("No numerical control spec provided for $param")
                val envelopeCode = control.points.code(warp = spec.warp)
                val busName = "~auxil_${name}_${param}"
                +"$busName  = Bus.control(s, 1)"
                +"{ $envelopeCode }.play(s, $busName)"
                +"${synthVar}.map(\\$param, $busName)"
            }

            is BusValueControl -> {
                val bus = control.bus.now.get()?.superColliderName ?: return unresolvedReference("Bus", control.bus.now)
                +"${synthVar}.map(\\$param, $bus)"
            }

            /* TODO is this needed?
                                is SingleBusValueControl -> {
                                    val bus = control.bus.now.get().superColliderName
                                    +"$bus.postln"
                                    +"${synthVar}.set(\\$param, $bus.value)"
                                }
            */

            is CustomControl -> {
                val spec = controls.get(param).spec.now as? NumericalControlSpec
                    ?: error("No numerical control spec provided for $param")
                val code = controlMap.entries
                    .associate { (name, control) -> "~ctrl_$name" to control.makeExpr(spec) }
                val expr = control.expr.editor.result.now
                    .transform<Identifier> { e -> code[e.text] ?: e }
                val busName = "~auxil_${name}_${param}"
                +"$busName = Bus.control(s, 1)"
                if (duration != null) {
                    appendBlock("", endLine = false) {
                        +"Env.new(levels: [0, 0], times: [$duration]).kr(Done.freeSelf)"
                        expr.code(writer, context)
                    }
                    +".play(s, $busName)"
                }
                +"${synthVar}.map(\\$param, $busName)"
            }

            else -> {} //already handled in constantArguments
        }
    }
}

private fun unresolvedReference(type: String, reference: ObjectReference<*>) {
    Logger.warn("Could not resolve $type $reference", Logger.Category.Playback)
}
