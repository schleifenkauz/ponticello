package xenakis.model.score.controls

import javafx.geometry.HorizontalDirection
import reaktive.value.now
import xenakis.impl.zero
import xenakis.model.flow.ScoreObjectInfo
import xenakis.model.obj.ParameterizedObject
import xenakis.model.score.ParameterControlList
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
    info: ScoreObjectInfo,
    spec: ControlSpec,
): ParameterControl {
    val control = if (info.cutoff != zero && this is EnvelopeControl) {
        spec as NumericalControlSpec
        val cutoffEnvelope = points.cut(info.cutoff, HorizontalDirection.RIGHT, spec.warp)
        EnvelopeControl(cutoffEnvelope)
    } else this
    return control
}

fun ScWriter.writeSynthCode(
    obj: ParameterizedObject,
    info: ScoreObjectInfo,
    controls: ParameterControlList,
    controlMap: Map<String, ParameterControl> = controls.controlMap,
) {
    val controlsWithSpecs = controlMap
        .mapValues { (name, ctrl) ->
            val spec = controls.get(name).spec.now!!
            val control = ctrl.adjustControlForCutoff(info, spec)
            Pair(control, spec)
        }
    val uniqueName = info.uniqueName(obj)
    val associatedServerObjects = mutableListOf<String>()
    for ((param, control) in controlsWithSpecs) {
        val (ctrl, spec) = control
        with(ctrl) {
            generatePreparationCode(obj, uniqueName, param, spec, associatedServerObjects)
        }
    }
    val synthDefName = obj.def.name.now
    val synthVar = "~synth_$uniqueName"
    append("$synthVar = Synth(\\$synthDefName, [")
    for ((param, control) in controlsWithSpecs) {
        val (ctrl, spec) = control
        if (!obj.def.hasParameter(param) && param !in SPECIAL_PARAMETERS) continue
        if (!ctrl.providesConstantSynthArgument()) continue
        val expr = ctrl.generateArgumentExpr(obj, uniqueName, param, spec)
        append("$param: ")
        expr.code(writer, controls.context)
        append(", ")
    }
    if (obj.duration() != null) append("duration: ${obj.duration()!!.now}")
    else append("afterDuration: Done.none")
    appendLine("], target: ${info.placement!!.target}, addAction: ${info.placement.addAction});")
    +"$synthVar.register"
    appendBlock("$synthVar.onFree ") {
        for (name in associatedServerObjects) {
            +"$name.free"
        }
    }
    for ((param, control) in controlsWithSpecs) {
        val (ctrl, spec) = control
        if (!obj.def.hasParameter(param) && param !in SPECIAL_PARAMETERS) continue
        with(ctrl) {
            applyToSynth(obj, synthVar, param, spec)
        }
    }
}