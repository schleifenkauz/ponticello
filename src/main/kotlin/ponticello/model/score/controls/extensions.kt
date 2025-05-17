package ponticello.model.score.controls

import ponticello.impl.Decimal
import ponticello.model.flow.NodePlacement
import ponticello.model.flow.NodePlacement.AddAction
import ponticello.model.obj.ParameterDefObject
import ponticello.model.obj.ParameterizedObject
import ponticello.model.score.ScoreObject
import ponticello.model.score.controls.ParameterControl.CodegenContext
import ponticello.sc.ControlSpec
import ponticello.sc.client.ScWriter
import reaktive.value.now

private val SPECIAL_PARAMETERS = setOf("auto_release")

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

fun ScWriter.writeSynthCode(
    obj: ParameterizedObject, uniqueName: String,
    cutoff: Decimal, placement: NodePlacement, latency: Decimal,
    extraArguments: Map<ParameterDefObject, ParameterControl> = emptyMap(),
) = appendBlock("fork") {
    +"var auxilBuses = (), auxilSynths = ()"
    val controlMap = createControlMap(obj, extraArguments)
    val synthDefName = obj.def.name.now
    val synthVar = "${obj.superColliderPrefix}$uniqueName"
    allocateAuxilMaps(uniqueName)
    +"s.sync"
    append("$synthVar = Synth.newPaused(\\$synthDefName, [")
    for ((param, control) in controlMap) {
        val (spec, ctrl) = control
        if (!obj.def.hasParameter(param) && param !in SPECIAL_PARAMETERS) continue
        val customSynthArgs = ctrl.customSynthArguments()
        if (customSynthArgs != null) append(customSynthArgs)
        if (!ctrl.providesConstantSynthArgument()) continue
        val expr = ctrl.generateArgumentExpr(obj, uniqueName, param, spec, context = CodegenContext.Synth)
        append("$param: ")
        expr.code(writer, obj.context)
        append(", ")
    }
    if (obj.duration() != null) append("duration: ${obj.duration()!!.now - cutoff}")
    else append("auto_release: 0")
    val action = guardAgainstReplaceNil(placement)
    appendLine("], target: ${placement.target}, addAction: $action);")
    +"s.sync"
    for ((param, control) in controlMap) {
        val (spec, ctrl) = control
        with(ctrl) {
            generatePreparationCode(obj, uniqueName, param, spec, cutoff, CodegenContext.Synth)
        }
    }
    if (controlMap.isNotEmpty()) +"s.sync"
    for ((param, control) in controlMap) {
        val (spec, ctrl) = control
        if (!obj.def.hasParameter(param) && param !in SPECIAL_PARAMETERS) continue
        with(ctrl) {
            applyToSynth(obj, uniqueName, synthVar, param, spec)
        }
    }
    +"$synthVar.register"
    appendBlock("s.makeBundle($latency / ~time_warp)") {
        +"$synthVar.run"
        +"${ParameterControl.auxilSynthsVar(uniqueName)}.do(_.run)"
    }
    appendBlock("$synthVar.onFree") {
        if (obj is ScoreObject) {
            +"~ponticello_addr.sendMsg('/freed', -1, \"$uniqueName\")" //TODO what if it was renamed
        }
        +"auxilBuses.do(_.free)"
        +"auxilSynths.do(_.free)"
    }
}

fun guardAgainstReplaceNil(placement: NodePlacement) = if (placement.addAction == AddAction.AddReplace)
    "if (${placement.target} != nil && ${placement.target}.isRunning) { 'addReplace' } { \"'${placement.target}' not found\".postln; ${AddAction.AddToTail} }"
else placement.addAction.toString()

fun ScWriter.writeProcessCode(
    obj: ParameterizedObject, uniqueName: String,
    cutoff: Decimal, latency: Decimal,
    extraArguments: Map<ParameterDefObject, ParameterControl> = emptyMap(),
) {
    val superColliderName = "~process_$uniqueName"
    val controlMap = createControlMap(obj, extraArguments)
    appendBlock("$superColliderName = Task", endLine = false) {
        for ((param, control) in controlMap) {
            val (spec, ctrl) = control
            with(ctrl) {
                generatePreparationCode(obj, uniqueName, param, spec, cutoff, ctx = CodegenContext.Process)
            }
        }
        allocateAuxilMaps(uniqueName)
        +"$latency.wait"
        val duration = obj.duration()?.now?.toString() ?: "inf"
        val defName = "proc_${obj.def.name.now}"
        append("$defName.value(t: $cutoff, duration: $duration")
        for ((param, control) in controlMap) {
            if (!obj.def.hasParameter(param)) continue
            val (spec, ctrl) = control
            val arg = ctrl.generateArgumentExpr(
                obj, uniqueName, param, spec,
                context = CodegenContext.Process
            )
            append(", $param: ")
            arg.code(writer, obj.context)
        }
        appendLine(");")
        if (obj is ScoreObject) {
            +"~ponticello_addr.sendMsg('/stopped', -1, ${uniqueName})"
        }
        +"auxilBuses.do(_.free)"
        +"auxilSynths.do(_.free)"
    }
    +".play"
}

private fun createControlMap(
    obj: ParameterizedObject,
    extraArguments: Map<ParameterDefObject, ParameterControl>,
): Map<String, Pair<ControlSpec, ParameterControl>> {
    val controlMap = obj.controls.all().associate { ctrl ->
        val spec = ctrl.spec.now!!
        ctrl.name.now to Pair(spec, ctrl.now)
    } + extraArguments.map { (param, control) -> param.name.now to Pair(param.spec.now, control) }
    return controlMap
}

private fun ScWriter.allocateAuxilMaps(uniqueName: String) {
    val auxilSynthsMap = ParameterControl.auxilSynthsVar(uniqueName)
    val auxilBusesMap = ParameterControl.auxilBusesVar(uniqueName)
    +"$auxilSynthsMap = auxilSynths"
    +"$auxilBusesMap = auxilBuses"
}
