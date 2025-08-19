package ponticello.model.score.controls

import ponticello.impl.Decimal
import ponticello.impl.zero
import ponticello.model.flow.NodePlacement
import ponticello.model.flow.NodePlacement.AddAction
import ponticello.model.obj.ParameterDefObject
import ponticello.model.obj.ParameterizedObject
import ponticello.model.score.ParameterControlList
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
    else -> null
}

fun ScWriter.writeSynthCode(
    obj: ParameterizedObject, uniqueName: String,
    cutoff: Decimal, placement: NodePlacement, latency: Decimal,
    controls: Map<ParameterDefObject, ParameterControl> = obj.controls.toMap(),
    run: Boolean = true,
) {
    +"var auxilBuses = (), auxilSynths = (), t0, delta_t"
    +"t0 = TempoClock.beats"
    val controlMap = createControlMap(controls)
    val synthDefName = obj.def.name.now
    val synthVar = "${obj.superColliderPrefix}$uniqueName"
    createAuxilMaps(uniqueName)
    +"s.sync"
    append("$synthVar = Synth.newPaused(\\$synthDefName, [")
    val duration = obj.duration()?.now
    for ((param, control) in controlMap) {
        val (spec, ctrl) = control
        if (!obj.def.hasParameter(param) && param !in SPECIAL_PARAMETERS) continue
        val customSynthArgs = ctrl.customSynthArguments(cutoff, duration ?: Decimal.INF)
        if (customSynthArgs != null) append(customSynthArgs)
        if (!ctrl.providesConstantSynthArgument(obj, spec, cutoff)) continue
        val expr = ctrl.generateArgumentExpr(obj, uniqueName, param, spec, cutoff, context = CodegenContext.Synth)
        append("$param: ")
        expr.code(writer, obj.context)
        append(", ")
    }
    if (duration != null) {
        val remainingDuration = duration - cutoff
        if (remainingDuration <= zero) throw AssertionError("Cutoff ($cutoff) > duration ($duration)")
        append("duration: $remainingDuration")
    } else append("auto_release: 0")
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
    if (latency != zero) +"delta_t = TempoClock.beats - t0"
    appendBlock("s.makeBundle(${if (latency != zero) "($latency - delta_t) / ~time_warp" else "nil"})") {
        if (run) +"$synthVar.run"
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
    obj: ParameterizedObject, uniqueName: String, cutoff: Decimal,
    controls: Map<ParameterDefObject, ParameterControl> = obj.controls.toMap(),
) {
    +"arg player_id"
    val superColliderName = "~proc_$uniqueName"
    val controlMap = createControlMap(obj.controls.toMap() + controls)
    appendBlock("$superColliderName = Task", endLine = false) {
        +"var auxilBuses = (), auxilSynths = (), t0, delta_t"
        +"~args_$uniqueName = ()"
        createAuxilMaps(uniqueName)
        for ((param, control) in controlMap) {
            val (spec, ctrl) = control
            with(ctrl) {
                generatePreparationCode(obj, uniqueName, param, spec, cutoff, ctx = CodegenContext.Process)
            }
        }
        val duration = obj.duration()?.now?.toString() ?: "inf"
        append("${obj.def.superColliderName}.value(player_id, t: $cutoff, duration: $duration")
        for ((param, control) in controlMap) {
            if (!obj.def.hasParameter(param)) continue
            val (spec, ctrl) = control
            val arg = ctrl.generateArgumentExpr(
                obj, uniqueName, param, spec, cutoff,
                context = CodegenContext.Process
            )
            append(", ${param}___: ")
            arg.code(writer, obj.context)
        }
        appendLine(");")
        if (obj is ScoreObject) {
            +"~ponticello_addr.sendMsg('/stopped', -1, \"${uniqueName}\")"
        }
        +"auxilBuses.do(_.free)"
        +"auxilSynths.do(_.free)"
    }
    +".play"
}

private fun createControlMap(
    controls: Map<ParameterDefObject, ParameterControl>,
): Map<String, Pair<ControlSpec, ParameterControl>> = controls.entries.associate { (param, control) ->
    param.name.now to Pair(param.spec.now, control)
}

fun ParameterControlList.toMap(): Map<ParameterDefObject, ParameterControl> = this.all().associate { ctrl ->
    val spec = ctrl.spec.now!!
    ParameterDefObject(ctrl.name.now, spec) to ctrl.now
}

private fun ScWriter.createAuxilMaps(uniqueName: String) {
    val auxilSynthsMap = ParameterControl.auxilSynthsVar(uniqueName)
    val auxilBusesMap = ParameterControl.auxilBusesVar(uniqueName)
    +"$auxilSynthsMap = auxilSynths"
    +"$auxilBusesMap = auxilBuses"
}
