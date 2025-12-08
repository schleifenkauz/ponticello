package ponticello.model.player

import ponticello.model.instr.ParameterizedObject
import ponticello.model.obj.SuperColliderObject
import ponticello.model.score.ScoreObject
import ponticello.model.score.controls.*
import ponticello.model.score.controls.ParameterControlList.NamedParameterControl
import ponticello.sc.ControlSpec
import ponticello.sc.NumericalControlSpec
import ponticello.sc.Warp
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.code
import reaktive.Observer
import reaktive.value.now
import reaktive.value.observe

class SoundProcessUpdater<O>(
    private val process: O
) : ParameterControlList.Listener, ScoreObject.Listener
        where O : ParameterizedObject, O : SuperColliderObject {
    private val controlObservers = mutableMapOf<ParameterControl, Observer>()

    private val client = process.context[SuperColliderClient]

    fun startListening() {
        process.controls.addListener(this, initialize = false)
        for (control in process.controls) {
            observeControl(control, control.now)
        }
        if (process is ScoreObject) {
            process.addListener(this)
        }
    }

    fun stopListening() {
        process.controls.removeListener(this)
        for ((_, observer) in controlObservers) observer.kill()
        controlObservers.clear()
    }

    override fun added(obj: NamedParameterControl, idx: Int) {
        observeControl(obj, obj.now)
        val code = obj.now.writeCode(obj.name.now, obj.spec.now, this.process)
        client.run("${this.process.superColliderName}.addControl($code, $idx)")
    }

    override fun removed(obj: NamedParameterControl, idx: Int) {
        controlObservers.remove(obj.now)?.kill()
        val parameter = obj.name.now
        val default = obj.spec.now?.defaultValueExpr
        client.run("${this.process.superColliderName}.removeControl('$parameter', $default)")
    }

    private fun observeControl(parameter: NamedParameterControl, control: ParameterControl) {
        controlObservers[control] = when (control) {
            is BusControl -> control.bus.observe { _, bus ->
                updateControl(parameter, "update(${bus.superColliderName})")
            }

            is BusValueControl -> control.bus.observe { _, bus ->
                updateControl(parameter, "update(${bus.superColliderName})")
            }

            is ValueControl -> control.value.observe { _, value ->
                updateControl(parameter, "update($value)")
            }

            is BufferControl -> control.sample.observe { _, buf ->
                updateControl(parameter, "update(${buf.superColliderName})")
            }

            is AttackReleaseControl -> return //TODO
            is EnvelopeControl -> control.update.stream.observe { _ ->
                val warp = (parameter.spec.now as? NumericalControlSpec)?.warp ?: Warp.Linear
                val code = control.points.code(warp)
                updateControl(parameter, "update($code)")
            }

            is UGenControl -> control.update.stream.observe { _ ->
                val expr = control.expr.editor.result.now
                val references = expr.parameterReferences().joinToString(", ") { name -> "'${name.getName()}'" }
                val subst = UGenControl.substituteParameterReferences(expr)
                val code = subst.code(control.context)
                updateControl(parameter, "update([$references]) { $code } ")
            }

            is ExprControl -> control.update.stream.observe { _ ->
                val expr = ExprControl.substituteParameterReferences(control.expr.editor.result.now)
                val code = expr.code(control.context)
                updateControl(parameter, "update { $code }")
            }
        }
    }

    override fun moved(obj: NamedParameterControl, idx: Int) {
        client.run("${process.superColliderName}.moveControl('${obj.name.now}', $idx)")
    }

    override fun reassignedControl(
        parameter: NamedParameterControl,
        oldControl: ParameterControl,
        newControl: ParameterControl,
    ) {
        controlObservers.remove(oldControl)?.kill()
        val spec = parameter.spec.now
        val paramName = parameter.name.now
        val code = newControl.writeCode(paramName, spec, process)
        client.run("${process.superColliderName}.replaceControl($code)")
        observeControl(parameter, newControl)
    }

    private fun updateControl(ctrl: NamedParameterControl, updateMessage: String) {
        val name = ctrl.name.now
        client.run("${process.superColliderName}.getControl('$name').$updateMessage")
    }

    override fun changedSpec(parameter: NamedParameterControl, oldSpec: ControlSpec?, newSpec: ControlSpec?) {
        val ctrl = parameter.now
        if (ctrl is ValueControl && oldSpec is NumericalControlSpec && newSpec is NumericalControlSpec) {
            if (oldSpec.allocateBus != newSpec.allocateBus) {
                updateControl(parameter, "setAllocateBus(${newSpec.allocateBus})")
            }
        }
    }
}