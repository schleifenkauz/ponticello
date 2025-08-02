package ponticello.model.player

import ponticello.impl.Decimal
import ponticello.impl.zero
import ponticello.model.flow.AudioFlow
import ponticello.model.flow.NodePlacement
import ponticello.model.obj.BufferReference
import ponticello.model.obj.BusReference
import ponticello.model.obj.ParameterizedObject
import ponticello.model.obj.SynthDefObject
import ponticello.model.registry.NamedObject.Companion.NO_NAME
import ponticello.model.score.ParameterControlList
import ponticello.model.score.ParameterControlList.NamedParameterControl
import ponticello.model.score.ScoreObject
import ponticello.model.score.controls.*
import ponticello.sc.NumericalControlSpec
import ponticello.sc.ScExpr
import ponticello.sc.client.ScWriter
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.run
import reaktive.Observer
import reaktive.and
import reaktive.value.now
import reaktive.value.observe

abstract class AbstractLiveUpdater(protected val obj: ParameterizedObject) : ParameterControlList.Listener {
    private val controlObservers = mutableMapOf<ParameterControl, Observer>()
    private var nameObserver: Observer? = null

    fun startListening() {
        obj.controls.addListener(this, initialize = false)
        for ((param, control) in obj.controls.controlMap) {
            observeControl(param, control)
        }
        nameObserver = syncNameWithSuperCollider()
    }

    private fun syncNameWithSuperCollider() = obj.name.observe { _, oldName, newName ->
        runOnActiveObjects { name, _ ->
            val suffix = if (obj is ScoreObject) name.substringAfterLast('_', "") else ""
            val prefix = if (obj is AudioFlow) "flow_" else ""
            val oldUniqueName = prefix + oldName + suffix
            val newUniqueName = prefix + newName + suffix
            val oldSupercolliderName = obj.superColliderPrefix + oldUniqueName
            val newSupercolliderName = obj.superColliderPrefix + newUniqueName
            +"$newSupercolliderName = $oldSupercolliderName"
            +"$oldSupercolliderName = nil"
            val oldBusesName = ParameterControl.auxilBusesVar(oldUniqueName)
            val newBusesName = ParameterControl.auxilBusesVar(newUniqueName)
            val oldSynthsName = ParameterControl.auxilSynthsVar(oldUniqueName)
            val newSynthsName = ParameterControl.auxilSynthsVar(newUniqueName)
            +"$newBusesName = $oldBusesName"
            +"$oldBusesName = nil"
            +"$newSynthsName = $oldSynthsName"
            +"$oldSynthsName = nil"
        }
    }

    fun stopListening() {
        obj.controls.removeListener(this)
        for ((_, observer) in controlObservers) observer.kill()
        controlObservers.clear()
        nameObserver?.kill()
        nameObserver = null
    }

    private fun runOnActiveObjects(action: ScWriter.(String, Decimal) -> Unit) {
        val client = obj.context[SuperColliderClient]
        val activeInstances = obj.activeObjects()
        if (activeInstances.isEmpty()) return
        client.run {
            for (obj in activeInstances) {
                val superColliderName = obj.superColliderName
                val uniqueName = obj.uniqueName.takeIf { it != NO_NAME } ?: superColliderName.removePrefix("~")
                val objectTime =
                    if (obj is ActiveScoreObject) obj.player.currentTime - obj.absolutePosition.time
                    else zero
                appendBlock("if ($superColliderName != nil)") {
                    action(uniqueName, objectTime)
                }
            }
        }
    }

    override fun added(obj: NamedParameterControl, idx: Int) {
        val parameter = obj.name.now
        val ctrl = obj.now
        addedControl(parameter, ctrl, replaceAuxilSynth = false, remap = true)
    }

    override fun removed(obj: NamedParameterControl) {
        controlObservers.remove(obj.now)?.kill()
        val parameter = obj.name.now
        val spec = obj.spec.now
        if (spec !is NumericalControlSpec) return
        freeServerObjectsAssociatedWith(parameter, obj)
        setDefault(parameter, spec)
    }

    override fun moved(obj: NamedParameterControl, idx: Int) {
        if (obj.now.hasOwnSynth(this.obj)) {
            val parameter = obj.name.now
            runOnActiveObjects { name, _ ->
                val target = getPlacementTarget(parameter, name)
                val synthName = ParameterControl.auxilSynthName(name, parameter)
                +"$synthName.moveBefore($target)"
            }
        }
    }

    private fun setDefault(parameter: String, spec: NumericalControlSpec) {
        runOnActiveObjects { name, _ ->
            updateValue(name, parameter, spec.defaultValue.get(), onBus = false, remap = false)
        }
    }

    private fun freeServerObjectsAssociatedWith(parameter: String, control: NamedParameterControl) {
        if (control.now.allocatesBus(obj)) freeParameterBuses(parameter)
        if (control.now.usesAuxilSynth(obj)) freeAuxilSynths(parameter)
    }

    private fun freeAuxilSynths(parameter: String) {
        runOnActiveObjects { name, _ ->
            val auxiliarySynthName = ParameterControl.auxilSynthName(name, parameter)
            +"$auxiliarySynthName.free"
            +"$auxiliarySynthName = nil"
        }
    }

    private fun freeParameterBuses(parameter: String) {
        runOnActiveObjects { name, _ ->
            val busName = ParameterControl.auxilBusName(name, parameter)
            +"$busName.free"
            +"$busName = nil"
        }
    }

    private fun allocateParameterBuses(parameter: NamedParameterControl) {
        runOnActiveObjects { name, _ ->
            val busName = ParameterControl.auxilBusName(name, parameter.name.now)
            +"$busName = Bus.control(s, 1)"
        }
    }

    private fun addedControl(parameter: String, ctrl: ParameterControl, replaceAuxilSynth: Boolean, remap: Boolean) {
        observeControl(parameter, ctrl)
        when (ctrl) {
            is BusControl -> runOnActiveObjects { name, _ ->
                updateBus(name, parameter, ctrl.bus.now)
            }

            is BusValueControl -> runOnActiveObjects { name, _ ->
                remapBus(name, parameter, ctrl.bus.now)
            }

            is ValueControl -> runOnActiveObjects { name, _ ->
                val onBus = obj.def is SynthDefObject && ctrl.allocateBus.now
                updateValue(name, parameter, ctrl.value.now, onBus, remap)
            }

            is BufferControl -> runOnActiveObjects { name, _ ->
                updateBuffer(name, parameter, ctrl.sample.now)
            }

            is AttackReleaseControl -> return //TODO

            is EnvelopeControl -> runOnActiveObjects { name, objectTime ->
                updateEnvelope(writer, objectTime, name, parameter, ctrl, remap, replaceAuxilSynth)
            }

            is UGenControl -> runOnActiveObjects { name, objectTime ->
                val expr = ctrl.expr.editor.result.now
                updateUGenControl(this, name, parameter, expr, replaceAuxilSynth, remap, objectTime)
            }

            is ExprControl -> runOnActiveObjects { name, _ ->
                val expr = ctrl.expr.editor.result.now
                updateExprControl(name, parameter, expr)
            }
        }
    }

    private fun observeControl(parameter: String, control: ParameterControl) {
        controlObservers[control] = when (control) {
            is BusControl -> control.bus.observe { _, bus ->
                runOnActiveObjects { name, _ ->
                    updateBus(name, parameter, bus)
                }
            }

            is BusValueControl -> control.bus.observe { _, bus ->
                runOnActiveObjects { name, _ ->
                    remapBus(name, parameter, bus)
                }
            }

            is ValueControl -> control.value.observe { _, value ->
                runOnActiveObjects { name, _ ->
                    val onBus = obj.def is SynthDefObject && control.allocateBus.now
                    updateValue(name, parameter, value, onBus, remap = false)
                }
            } and control.allocateBus.observe { _, allocateBus ->
                runOnActiveObjects { name, _ ->
                    updateValueControlMode(name, parameter, allocateBus, control.value.now)
                }
            }

            is BufferControl -> control.sample.observe { _, buf ->
                runOnActiveObjects { name, _ ->
                    updateBuffer(name, parameter, buf)
                }
            }

            is AttackReleaseControl -> return //TODO
            is EnvelopeControl -> control.update.stream.observe { _ ->
                runOnActiveObjects { name, objectTime ->
                    updateEnvelope(writer, objectTime, name, parameter, control, remap = false, replaceAuxilSynth = true)
                }
            }

            is UGenControl -> control.update.stream.observe { _ ->
                runOnActiveObjects { name, objectTime ->
                    val expr = control.expr.editor.result.now
                    updateUGenControl(this, name, parameter, expr, replace = true, remap = false, objectTime)
                }
            }
            is ExprControl -> control.update.stream.observe { _ ->
                runOnActiveObjects { name, _ ->
                    val expr = control.expr.editor.result.now
                    updateExprControl(name, parameter, expr)
                }
            }
        }
    }

    override fun reassignedControl(
        parameter: NamedParameterControl,
        oldControl: ParameterControl,
        newControl: ParameterControl,
    ) {
        controlObservers.remove(oldControl)?.kill()
        if (oldControl.allocatesBus(obj) && !newControl.allocatesBus(obj)) {
            freeParameterBuses(parameter.name.now)
        }
        if (!oldControl.allocatesBus(obj) && newControl.allocatesBus(obj)) {
            allocateParameterBuses(parameter)
        }
        if (oldControl.usesAuxilSynth(obj) && !newControl.usesAuxilSynth(obj)) {
            freeAuxilSynths(parameter.name.now)
        }
        val replaceAuxilSynth = oldControl.usesAuxilSynth(obj)
        val remap = !oldControl.allocatesBus(obj) && newControl.allocatesBus(obj)
        addedControl(parameter.name.now, newControl, replaceAuxilSynth, remap)
    }

    protected abstract fun ScWriter.updateValue(
        uniqueName: String, parameter: String, value: Decimal,
        onBus: Boolean, remap: Boolean,
    )

    protected open fun ScWriter.updateValueControlMode(
        uniqueName: String, parameter: String,
        allocateBus: Boolean, currentValue: Decimal,
    ) {
    }

    protected abstract fun ScWriter.updateBus(uniqueName: String, parameter: String, bus: BusReference)

    protected abstract fun ScWriter.updateValueBus(uniqueName: String, parameter: String, bus: BusReference)

    protected abstract fun ScWriter.remapBus(uniqueName: String, parameter: String, bus: BusReference)

    protected abstract fun ScWriter.updateBuffer(uniqueName: String, parameter: String, buf: BufferReference)

    protected abstract fun updateEnvelope(
        writer: ScWriter, objectTime: Decimal,
        uniqueName: String, parameter: String, envelope: EnvelopeControl,
        remap: Boolean,
        replaceAuxilSynth: Boolean,
    )

    protected open fun updateUGenControl(
        writer: ScWriter, uniqueName: String, parameter: String,
        expr: ScExpr, replace: Boolean, remap: Boolean, objectTime: Decimal
    ) {
        val auxiliarySynthName = ParameterControl.auxilSynthName(uniqueName, parameter)
        val substituted = UGenControl.substituteControlParameters(expr, obj, uniqueName, objectTime)
        val busName = ParameterControl.auxilBusName(uniqueName, parameter)
        writer.append("$auxiliarySynthName = ")
        writer.appendBlock("", endLine = false) {
            substituted.code(writer, obj.context)
        }
        val placement = getAuxiliarySynthPlacement(parameter, uniqueName, replace)
        val action = guardAgainstReplaceNil(placement)
        writer.appendLine(".play(target: ${placement.target}, outbus: $busName, fadeTime: ${AttackReleaseControl.DEFAULT}, addAction: ${action});")
    }

    protected abstract fun ScWriter.updateExprControl(uniqueName: String, parameter: String, expr: ScExpr)

    protected fun getAuxiliarySynthPlacement(parameter: String, uniqueName: String, replace: Boolean) = when {
        replace -> NodePlacement.replace(ParameterControl.auxilSynthName(uniqueName, parameter))
        else -> {
            val placementTarget = getPlacementTarget(parameter, uniqueName)
            NodePlacement.before(placementTarget)
        }
    }

    private fun getPlacementTarget(parameter: String, uniqueName: String): String {
        val parametersWithSynth = obj.controls
            .filter { ctrl -> ctrl.now.hasOwnSynth(obj) }
            .map { ctrl -> ctrl.name.now }
        val idx = parametersWithSynth.indexOf(parameter)
        return if (idx == -1 || idx == parametersWithSynth.size - 1) {
            val mainSynthName = "${obj.superColliderPrefix}$uniqueName"
            mainSynthName
        } else {
            ParameterControl.auxilSynthName(parametersWithSynth[idx + 1], uniqueName)
        }
    }
}