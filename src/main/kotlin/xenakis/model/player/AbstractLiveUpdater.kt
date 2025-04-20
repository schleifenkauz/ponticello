package xenakis.model.player

import reaktive.Observer
import reaktive.value.now
import reaktive.value.observe
import xenakis.impl.Decimal
import xenakis.model.obj.BufferReference
import xenakis.model.obj.BusReference
import xenakis.model.obj.GlobalPatternReference
import xenakis.model.obj.ParameterizedObject
import xenakis.model.score.Envelope
import xenakis.model.score.ParameterControlList
import xenakis.model.score.controls.*
import xenakis.sc.NumericalControlSpec
import xenakis.sc.ScExpr
import xenakis.sc.client.ScWriter
import xenakis.sc.client.SuperColliderClient

abstract class AbstractLiveUpdater(protected val obj: ParameterizedObject) : ParameterControlList.Listener {
    private val controlObservers = mutableMapOf<ParameterControl, Observer>()

    private lateinit var defObserver: Observer

    fun listen(controls: ParameterControlList) {
        controls.addListener(this, initialize = false)
        for ((param, control) in controls.controlMap) {
            observeControl(param, control)
        }
        defObserver = obj.def.updated.observe { _ ->
            obj.context[SuperColliderClient].run {
                updatedDefinition()
            }
        }
    }

    fun stopListening() {
        obj.controls.removeListener(this)
        for ((_, observer) in controlObservers) observer.kill()
        controlObservers.clear()
        defObserver.kill()
    }

    protected fun runOnActiveObjects(action: ScWriter.(String, Decimal) -> Unit) {
        val client = obj.context[SuperColliderClient]
        val playbackManager = obj.context[PlaybackManager]
        val activeInstances = obj.activeInstances()
        if (activeInstances.isEmpty()) return
        client.run {
            for ((_, pos, suffix) in activeInstances) {
                val name = ActiveObjectManager.uniqueName(obj.name.now, suffix)
                val superColliderName = "${obj.superColliderPrefix}_$name"
                val objectTime = playbackManager.playHead.currentTime - pos.time
                println("$name ($superColliderName) at $objectTime")
                appendBlock("if ($superColliderName != nil)") {
                    action(name, objectTime)
                }
            }
        }
    }

    override fun added(obj: ParameterControlList.NamedParameterControl, idx: Int) {
        val parameter = obj.name.now
        val ctrl = obj.now
        addedControl(parameter, ctrl)
    }

    override fun removed(obj: ParameterControlList.NamedParameterControl) {
        controlObservers.remove(obj.now)?.kill()
        val parameter = obj.name.now
        val spec = obj.spec.now
        if (spec !is NumericalControlSpec) return
        setDefault(parameter, spec)
        freeServerObjectsAssociatedWith(parameter, spec, obj)
    }

    private fun setDefault(parameter: String, spec: NumericalControlSpec) {
        runOnActiveObjects { name, _ ->
            updateValue(name, parameter, spec.defaultValue.get())
        }
    }

    private fun freeServerObjectsAssociatedWith(
        parameter: String,
        spec: NumericalControlSpec,
        obj: ParameterControlList.NamedParameterControl,
    ) {
        when (obj.now) {
            is EnvelopeControl -> runOnActiveObjects { name, _ ->
                val auxiliarySynthName = EnvelopeControl.envSynthName(name, parameter)
                +"$auxiliarySynthName.free"
                val busName = ParameterControl.uniqueArgumentName(name, parameter)
                +"$busName.free"
                updateValue(name, parameter, spec.defaultValue.get())
            }

            is AttackReleaseControl -> TODO()
            is UGenControl -> runOnActiveObjects { name, _ ->
                val synthName = UGenControl.synthName(name, parameter)
                +"$synthName.free"
                val busName = ParameterControl.uniqueArgumentName(name, parameter)
                +"$busName.free"
            }

            else -> {}
        }
    }

    private fun addedControl(parameter: String, ctrl: ParameterControl) {
        observeControl(parameter, ctrl)
        when (ctrl) {
            is BusControl -> runOnActiveObjects { name, _ ->
                updateBus(name, parameter, ctrl.bus.now)
            }

            is BusValueControl -> runOnActiveObjects { name, _ ->
                remapBus(name, parameter, ctrl.bus.now)
            }

            is ValueControl -> runOnActiveObjects { name, _ ->
                updateValue(name, parameter, ctrl.value.now)
            }

            is BufferControl -> runOnActiveObjects { name, _ ->
                updateBuffer(name, parameter, ctrl.sample.now)
            }

            is AttackReleaseControl -> return //TODO

            is EnvelopeControl -> return //TODO

            is GlobalPatternControl -> runOnActiveObjects { name, _ ->
                updatePattern(name, parameter, ctrl.pattern.now)
            }

            is SingleBusValueControl -> runOnActiveObjects { name, _ ->
                updateValueBus(name, parameter, ctrl.bus.now)
            }

            is UGenControl -> return //TODO

            else -> return //no real-time update possible

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
                    updateValue(name, parameter, value)
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
                    updateEnvelope(writer, objectTime, name, parameter, envelope = control.points)
                }
            }
            is GlobalPatternControl -> control.pattern.observe { _, pattern ->
                runOnActiveObjects { name, _ ->
                    updatePattern(name, parameter, pattern)
                }
            }

            is SingleBusValueControl -> control.bus.observe { _, bus ->
                runOnActiveObjects { name, _ ->
                    updateValueBus(name, parameter, bus)
                }
            }

            is UGenControl -> control.update.stream.observe { _ ->
                runOnActiveObjects { name, _ ->
                    val expr = control.expr.editor.result.now
                    updateUGenControl(this, name, parameter, expr)
                }
            }

            else -> return //no real-time update possible
        }
    }

    override fun reassignedControl(
        namedControl: ParameterControlList.NamedParameterControl,
        oldControl: ParameterControl,
        control: ParameterControl,
    ) {
        controlObservers.remove(oldControl)?.kill()
        addedControl(namedControl.name.now, control)
    }

    protected abstract fun ScWriter.updatedDefinition()

    protected abstract fun ScWriter.updateValue(uniqueName: String, parameter: String, value: Decimal)

    protected abstract fun ScWriter.updateBus(uniqueName: String, parameter: String, bus: BusReference)

    protected abstract fun ScWriter.updateValueBus(uniqueName: String, parameter: String, bus: BusReference)

    protected abstract fun ScWriter.remapBus(uniqueName: String, parameter: String, bus: BusReference)

    protected abstract fun ScWriter.updateBuffer(uniqueName: String, parameter: String, buf: BufferReference)

    protected abstract fun ScWriter.updatePattern(
        uniqueName: String,
        parameter: String,
        pattern: GlobalPatternReference,
    )

    protected abstract fun updateEnvelope(
        writer: ScWriter, objectTime: Decimal,
        uniqueName: String, parameter: String, envelope: Envelope,
    )

    protected open fun updateUGenControl(writer: ScWriter, uniqueName: String, parameter: String, expr: ScExpr) {
        val auxiliarySynthName = UGenControl.synthName(uniqueName, parameter)
        val substituted = UGenControl.substituteControlParameters(expr, obj, uniqueName)
        val busName = ParameterControl.uniqueArgumentName(uniqueName, parameter)
        writer.append("$auxiliarySynthName = ")
        writer.appendBlock("", endLine = false) {
            substituted.code(writer, obj.context)
        }
        writer.appendLine(".play($auxiliarySynthName, $busName, fadeTime: 0.02, addAction: 'addReplace')")

    }
}