package xenakis.model.score.controls

import javafx.geometry.HorizontalDirection
import reaktive.value.now
import xenakis.impl.Decimal
import xenakis.impl.zero
import xenakis.model.flow.NodePlacement
import xenakis.model.flow.NodePlacement.AddAction
import xenakis.model.obj.ParameterizedObject
import xenakis.model.score.ScoreObject
import xenakis.model.score.controls.ParameterControl.CodegenContext
import xenakis.sc.ControlSpec
import xenakis.sc.NumericalControlSpec
import xenakis.sc.client.ScWriter

private val SPECIAL_PARAMETERS = setOf("afterDuration")

fun ParameterControl.getNumericalValue() = when (this) {
    is ValueControl -> value.now
    is EnvelopeControl -> points.points.first().value
    else -> null
}

fun ParameterControl.getBus() = when (this) {
    is BusControl -> bus.now
    is BusValueControl -> bus.now
    is SingleBusValueControl -> bus.now
    else -> null
}

private fun ParameterControl.adjustControlForCutoff(
    cutoff: Decimal,
    spec: ControlSpec,
): ParameterControl {
    val control = if (cutoff != zero && this is EnvelopeControl) {
        spec as NumericalControlSpec
        val cutoffEnvelope = points.cut(cutoff, HorizontalDirection.RIGHT, spec.warp)
        EnvelopeControl(cutoffEnvelope)
    } else this
    return control
}

fun ScWriter.writeSynthCode(
    obj: ParameterizedObject, uniqueName: String,
    cutoff: Decimal, placement: NodePlacement, latency: Decimal,
) {
    appendBlock("s.makeBundle($latency)") {
        +"var auxilBuses = (), auxilSynths = ()"
        val controlsWithSpecs = obj.controls.all().associate { ctrl ->
            val spec = ctrl.spec.now!!
            val control = ctrl.now.adjustControlForCutoff(cutoff, spec)
            ctrl.name.now to Pair(spec, control)
        }
        val synthDefName = obj.def.name.now
        val synthVar = "${obj.superColliderPrefix}$uniqueName"
        allocateAuxilMaps(uniqueName)
        append("$synthVar = Synth.newPaused(\\$synthDefName, [")
        for ((param, control) in controlsWithSpecs) {
            val (spec, ctrl) = control
            if (!obj.def.hasParameter(param) && param !in SPECIAL_PARAMETERS) continue
            if (!ctrl.providesConstantSynthArgument()) continue
            val expr = ctrl.generateArgumentExpr(obj, uniqueName, param, spec, context = CodegenContext.Synth)
            append("$param: ")
            expr.code(writer, obj.context)
            append(", ")
        }
        if (obj.duration() != null) append("duration: ${obj.duration()!!.now}")
        else append("afterDuration: Done.none")
        val action = guardAgainstReplaceNil(placement)
        appendLine("], target: ${placement.target}, addAction: $action);")
        +"s.sync"
        for ((param, control) in controlsWithSpecs) {
            val (spec, ctrl) = control
            with(ctrl) {
                generatePreparationCode(obj, uniqueName, param, spec, CodegenContext.Synth)
            }
        }
        +"s.sync"
        for ((param, control) in controlsWithSpecs) {
            val (spec, ctrl) = control
            if (!obj.def.hasParameter(param) && param !in SPECIAL_PARAMETERS) continue
            with(ctrl) {
                applyToSynth(obj, uniqueName, synthVar, param, spec)
            }
        }
        +"$synthVar.register"
        +"$synthVar.run"
        appendBlock("$synthVar.onFree") {
            if (obj is ScoreObject) {
                +"~xenakis_addr.sendMsg('/freed', -1, \"$uniqueName\")" //TODO what if it was renamed
            }
            +"auxilBuses.do(_.free)"
            +"auxilSynths.do(_.free)"
        }
    }
}

fun guardAgainstReplaceNil(placement: NodePlacement) = if (placement.addAction == AddAction.AddReplace)
    "if (${placement.target} != nil) { 'addReplace' } { \"'${placement.target}' not found\".postln; ${AddAction.AddToTail} }"
else placement.addAction.toString()

fun ScWriter.writeProcessCode(
    obj: ParameterizedObject,
    uniqueName: String,
    cutoff: Decimal,
    latency: Decimal,
) {
    val superColliderName = "~process_$uniqueName"
    appendBlock("$superColliderName = Task", endLine = false) {
        for (control in obj.controls) {
            val spec = control.spec.now!!
            val name = control.name.now
            with(control.now) {
                generatePreparationCode(
                    obj, uniqueName,
                    name, spec, context = CodegenContext.Process
                )
            }
        }
        allocateAuxilMaps(uniqueName)
        +"$latency.wait"
        val duration = obj.duration()?.now?.toString() ?: "inf"
        val defName = "proc_${obj.def.name.now}"
        append("$defName.value(t: $cutoff, duration: $duration")
        for (control in obj.controls) {
            if (!obj.def.hasParameter(control.name.now)) continue
            val name = control.name.now
            val spec = control.spec.now!!
            val arg = control.now.generateArgumentExpr(
                obj, uniqueName, name, spec,
                context = CodegenContext.Process
            )
            append(", $name: ")
            arg.code(writer, obj.context)
        }
        appendLine(");")
        if (obj is ScoreObject) {
            +"~xenakis_addr.sendMsg('/stopped', -1, ${uniqueName})"
        }
        +"auxilBuses.do(_.free)"
        +"auxilSynths.do(_.free)"
    }
    +".play"
}

private fun ScWriter.allocateAuxilMaps(uniqueName: String) {
    val auxilSynthsMap = ParameterControl.auxilSynthsVar(uniqueName)
    val auxilBusesMap = ParameterControl.auxilBusesVar(uniqueName)
    +"$auxilSynthsMap = auxilSynths"
    +"$auxilBusesMap = auxilBuses"
}
