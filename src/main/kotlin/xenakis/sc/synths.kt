package xenakis.sc

import reaktive.value.now
import xenakis.model.flow.ScoreObjectInfo
import xenakis.model.obj.ParameterizedObject
import xenakis.model.score.ParameterControlList
import xenakis.model.score.controls.ParameterControl
import xenakis.sc.client.ScWriter

private val SPECIAL_PARAMETERS = setOf("afterDuration")

fun ScWriter.writeSynthCode(
    obj: ParameterizedObject,
    info: ScoreObjectInfo,
    controls: ParameterControlList,
    controlMap: Map<String, ParameterControl> = controls.controlMap,
) {
    val synthDefName = obj.def.name.now
    val synthVar = "~synth_${info.uniqueName(obj)}"
    append("$synthVar = Synth(\\$synthDefName, [")
    for ((param, ctrl) in controlMap) {
        if (!obj.def.hasParameter(param) && param !in SPECIAL_PARAMETERS) continue
        if (!ctrl.providesConstantSynthArgument()) continue
        val spec = controls.get(param).spec.now!!
        val expr = ctrl.generateCodeFor(obj, spec)
        append("$param: ")
        expr.code(writer, controls.context)
        append(", ")
    }
    if (obj.duration() != null) append("duration: ${obj.duration()!!.now}")
    appendLine("], target: ${info.placement!!.target}, addAction: ${info.placement.addAction});")
    +"$synthVar.register"
    for ((param, control) in controlMap) {
        if (!obj.def.hasParameter(param) && param !in SPECIAL_PARAMETERS) continue
        val spec = controls.get(param).spec.now!!
        with(control) {
            applyToSynth(param, spec, obj, synthVar)
        }
    }
}
