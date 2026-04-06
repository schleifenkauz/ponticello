package ponticello.model.player

import ponticello.model.instr.ParameterizedObject
import ponticello.model.score.controls.*
import ponticello.sc.ControlSpec
import ponticello.sc.NumericalControlSpec
import ponticello.sc.Warp
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.code
import reaktive.Observer
import reaktive.and
import reaktive.dependencies
import reaktive.value.now
import reaktive.value.observe

class SoundProcessUpdater<O>(
    private val obj: O
) : ParameterControlList.Listener where O : ParameterizedObject {
    private val controlObservers = mutableMapOf<ParameterControl, Observer>()
    private lateinit var instrumentObserver: Observer

    private val client = obj.context[SuperColliderClient]

    fun startListening() {
        obj.controls.addListener(this, initialize = false)
        for (control in obj.controls) {
            observeControl(control, control.now)
        }
        instrumentObserver = obj.instrumentChanged.observe { _ ->
            updateSoundProcess("setInstrument(${obj.getInstrument().superColliderName})")
        }
    }

    fun stopListening() {
        obj.controls.removeListener(this)
        for ((_, observer) in controlObservers) observer.kill()
        controlObservers.clear()
        instrumentObserver.kill()
    }

    override fun added(obj: NamedParameterControl, idx: Int) {
        observeControl(obj, obj.now)
        val code = obj.now.writeCode(obj.name.now, obj.spec.now, this.obj)
        updateSoundProcess("addControl($code, $idx)")
    }

    override fun removed(obj: NamedParameterControl, idx: Int) {
        controlObservers.remove(obj.now)?.kill()
        val parameter = obj.name.now
        updateSoundProcess("removeControl('$parameter')")
    }

    private fun observeControl(parameter: NamedParameterControl, control: ParameterControl) {
        controlObservers[control] = when (control) {
            is BusControl -> dependencies(control.bus, control.offset).observe { _ ->
                val varName = control.bus.now.superColliderName
                val offset = control.offset.now
                val busExpr = if (offset == 0) varName else "$varName.subBus($offset)"
                updateControl(parameter, "update($busExpr)")
            }

            is BusValueControl -> control.bus.observe { _, bus ->
                updateControl(parameter, "update(${bus.superColliderName})")
            } and control.offset.observe { _, offset ->
                updateControl(parameter, "offset = $offset")
            }

            is ValueControl -> control.value.observe { _, value ->
                updateControl(parameter, "update($value)")
            }

            is BufferControl -> control.sample.observe { _, buf ->
                updateControl(parameter, "update(${buf.superColliderName})")
            }

            is AttackReleaseControl -> control.attack.observe { _, _, att ->
                updateControl(parameter, "attack = $att")
            } and control.release.observe { _, _, rel ->
                updateControl(parameter, "release = $rel")
            }

            is EnvelopeControl -> control.updated.observe { _ ->
                val warp = (parameter.spec.now as? NumericalControlSpec)?.warp ?: Warp.Linear
                val code = control.points.code(warp)
                updateControl(parameter, "update($code)")
            }

            is UGenControl -> control.update.stream.observe { _ ->
                val expr = control.expr.editor.result.now
                val (subst, references) = UGenControl.substituteParameterReferences(expr, obj)
                val code = subst.code(control.context)
                updateControl(parameter, "update($references) { $code } ")
            }

            is ExprControl -> control.update.stream.observe { _ ->
                var expr = control.expr.editor.result.now
                expr = ExprControl.substituteParameterReferences(expr, obj)
                val code = expr.code(control.context)
                updateControl(parameter, "update { |inst| $code }")
            }
            is TriggerControl -> control.trigger.stream.observe { _ ->
                updateControl(parameter, "trigger")
            }
        }
    }

    override fun moved(obj: NamedParameterControl, idx: Int) {
        updateSoundProcess("moveControl('${obj.name.now}', $idx)")
    }

    override fun reassignedControl(
        parameter: NamedParameterControl,
        oldControl: ParameterControl,
        newControl: ParameterControl,
    ) {
        controlObservers.remove(oldControl)?.kill()
        val spec = parameter.spec.now
        val paramName = parameter.name.now
        val code = newControl.writeCode(paramName, spec, obj)
        updateSoundProcess("replaceControl($code)")
        observeControl(parameter, newControl)
    }

    override fun renamedControl(parameter: NamedParameterControl, oldName: String, newName: String) {
        updateSoundProcess("renameControl('$oldName', '$newName')")
    }

    override fun changedSpec(parameter: NamedParameterControl, oldSpec: ControlSpec?, newSpec: ControlSpec?) {
        val ctrl = parameter.now
        if (ctrl is ValueControl && oldSpec is NumericalControlSpec && newSpec is NumericalControlSpec) {
            if (oldSpec.allocateBus != newSpec.allocateBus) {
                updateControl(parameter, "setAllocateBus(${newSpec.allocateBus})")
            }
        }
    }

    private fun updateSoundProcess(message: String) {
        if (obj.isCreatedInSuperCollider) {
            client.run("SoundProcess.get('${obj.soundProcessName}').$message")
        }
    }

    private fun updateControl(ctrl: NamedParameterControl, updateMessage: String) {
        val name = ctrl.name.now
        updateSoundProcess("getControl('$name').$updateMessage")
    }
}