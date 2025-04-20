package xenakis.model.score.controls

import javafx.geometry.HorizontalDirection
import reaktive.value.now
import xenakis.impl.Decimal
import xenakis.impl.zero
import xenakis.model.flow.NodePlacement
import xenakis.model.obj.ParameterizedObject
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
    obj: ParameterizedObject,
    uniqueName: String,
    cutoff: Decimal,
    placement: NodePlacement,
    latency: Decimal,
    extraControls: Map<String, Pair<ControlSpec, ParameterControl>> = emptyMap(),
    customSynthVar: String? = null,
) {
    appendBlock("s.makeBundle($latency)") {
        val controlsWithSpecs = obj.controls.all().associate { ctrl ->
            val spec = ctrl.spec.now!!
            val control = ctrl.now.adjustControlForCutoff(cutoff, spec)
            ctrl.name.now to Pair(spec, control)
        } + extraControls
        val synthDefName = obj.def.name.now
        val synthVar = customSynthVar ?: "~synth_$uniqueName"
        append("$synthVar = Synth.newPaused(\\$synthDefName, [")
        for ((param, control) in controlsWithSpecs) {
            val (spec, ctrl) = control
            if (!obj.def.hasParameter(param) && param !in SPECIAL_PARAMETERS) continue
            if (!ctrl.providesConstantSynthArgument()) continue
            val expr = ctrl.generateArgumentExpr(obj, uniqueName, param, spec)
            append("$param: ")
            expr.code(writer, obj.context)
            append(", ")
        }
        if (obj.duration() != null) append("duration: ${obj.duration()!!.now}")
        else append("afterDuration: Done.none")
        appendLine("], target: ${placement.target}, addAction: ${placement.addAction});")
        +"s.sync"
        +"$synthVar.register"
        val associatedServerObjects = mutableListOf<String>()
        for ((param, control) in controlsWithSpecs) {
            val (spec, ctrl) = control
            with(ctrl) {
                generatePreparationCode(obj, uniqueName, param, spec, associatedServerObjects)
            }
        }
        if (associatedServerObjects.isNotEmpty()) {
            appendBlock("$synthVar.onFree ") {
                for (name in associatedServerObjects) {
                    +"$name.free"
                    +"$name = nil"
                }
                +"$synthVar = nil"
            }
        }
        +"s.sync"
        for ((param, control) in controlsWithSpecs) {
            val (spec, ctrl) = control
            if (!obj.def.hasParameter(param) && param !in SPECIAL_PARAMETERS) continue
            with(ctrl) {
                applyToSynth(obj, synthVar, param, spec)
            }
        }
        +"$synthVar.run"
    }
}

// Synth(\sine, [freq: 400, amp: 1, out: s.outputBus, pan: 0, group: s.defaultGroup, duration: 5.0000], target: s.defaultGroup, addAction: 'addToHead')