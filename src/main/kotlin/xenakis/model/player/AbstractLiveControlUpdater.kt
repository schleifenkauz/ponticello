package xenakis.model.player

import kotlinx.serialization.Transient
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

abstract class AbstractLiveControlUpdater(protected val obj: ParameterizedObject) : ParameterControlList.Listener {
    @Transient
    private val controlObservers = mutableMapOf<ParameterControl, Observer>()

    private fun runOnActiveObjects(action: ScWriter.(String) -> Unit) {
        val client = obj.context[SuperColliderClient]
        client.run {
            for (name in obj.activeInstances()) {
                val superColliderName = "${obj.superColliderPrefix}_$name"
                appendBlock("if ($superColliderName != nil)") {
                    action(name)
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
        runOnActiveObjects { name ->
            updateValue(name, parameter, spec.defaultValue.get())
        }
    }

    private fun freeServerObjectsAssociatedWith(
        parameter: String,
        spec: NumericalControlSpec,
        obj: ParameterControlList.NamedParameterControl,
    ) {
        when (obj.now) {
            is EnvelopeControl -> runOnActiveObjects { name ->
                val auxiliarySynthName = EnvelopeControl.envSynthName(name, parameter)
                +"$auxiliarySynthName.free"
                val busName = ParameterControl.uniqueArgumentName(name, parameter)
                +"$busName.free"
                updateValue(name, parameter, spec.defaultValue.get())
            }

            is AttackReleaseControl -> TODO()
            is UGenControl -> runOnActiveObjects { name ->
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
            is BusControl -> runOnActiveObjects { name ->
                updateBus(name, parameter, ctrl.bus.now)
            }

            is BusValueControl -> runOnActiveObjects { name ->
                remapBus(name, parameter, ctrl.bus.now)
            }

            is ValueControl -> runOnActiveObjects { name ->
                updateValue(name, parameter, ctrl.value.now)
            }

            is BufferControl -> runOnActiveObjects { name ->
                updateBuffer(name, parameter, ctrl.sample.now)
            }

            is AttackReleaseControl -> return //TODO

            is EnvelopeControl -> return //TODO

            is GlobalPatternControl -> runOnActiveObjects { name ->
                updatePattern(name, parameter, ctrl.pattern.now)
            }

            is SingleBusValueControl -> runOnActiveObjects { name ->
                updateValueBus(name, parameter, ctrl.bus.now)
            }

            is UGenControl -> return //TODO

            else -> return //no real-time update possible

        }
    }

    private fun observeControl(parameter: String, control: ParameterControl) {
        controlObservers[control] = when (control) {
            is BusControl -> control.bus.observe { _, bus ->
                runOnActiveObjects { name ->
                    updateBus(name, parameter, bus)
                }
            }

            is BusValueControl -> control.bus.observe { _, bus ->
                runOnActiveObjects { name ->
                    remapBus(name, parameter, bus)
                }
            }

            is ValueControl -> control.value.observe { _, value ->
                runOnActiveObjects { name ->
                    updateValue(name, parameter, value)
                }
            }

            is BufferControl -> control.sample.observe { _, buf ->
                runOnActiveObjects { name ->
                    updateBuffer(name, parameter, buf)
                }
            }

            is AttackReleaseControl -> return //TODO
            is EnvelopeControl -> return //TODO
            is GlobalPatternControl -> control.pattern.observe { _, pattern ->
                runOnActiveObjects { name ->
                    updatePattern(name, parameter, pattern)
                }
            }

            is SingleBusValueControl -> control.bus.observe { _, bus ->
                runOnActiveObjects { name ->
                    updateValueBus(name, parameter, bus)
                }
            }

            is UGenControl -> control.update.stream.observe { _ ->
                runOnActiveObjects { name ->
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

    fun listen(controls: ParameterControlList) {
        controls.addListener(this, initialize = false)
        for ((param, control) in controls.controlMap) {
            observeControl(param, control)
        }
    }

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

    protected open fun updateEnvelope(writer: ScWriter, uniqueName: String, parameter: String, envelope: Envelope) {
        val auxiliarySynthName = EnvelopeControl.envSynthName(uniqueName, parameter)
        writer.appendLine("$auxiliarySynthName.free;")
        val auxiliaryBus = ParameterControl.uniqueArgumentName(uniqueName, parameter)
        val spec = obj.getSpec(parameter) as? NumericalControlSpec ?: return
        val envelopeCode = envelope.code(spec.warp) //TODO take into consideration where the play head is...
        writer.appendLine("$auxiliarySynthName = { $envelopeCode.kr }.play(s, $auxiliaryBus);")
    }

    protected open fun updateUGenControl(writer: ScWriter, uniqueName: String, parameter: String, expr: ScExpr) {
        val synthName = UGenControl.synthName(uniqueName, parameter)
        writer.appendLine("$synthName.free;")
        val spec = obj.getSpec(parameter) ?: return
        val substituted = UGenControl.substituteControlParameters(expr, spec, obj, uniqueName)
        val busName = ParameterControl.uniqueArgumentName(uniqueName, parameter)
        UGenControl.createSynth(writer, busName, substituted, obj.context, UGenControl.synthName(uniqueName, parameter))
    }
}