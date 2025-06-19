package ponticello.ui.misc

import ponticello.model.score.ParameterControlList
import ponticello.model.score.ParameterControlList.NamedParameterControl
import ponticello.model.score.controls.EnvelopeControl
import ponticello.model.score.controls.ParameterControl
import ponticello.model.score.controls.UGenControl
import ponticello.model.score.controls.ValueControl
import ponticello.sc.*
import reaktive.Observer
import reaktive.and
import reaktive.event.event
import reaktive.event.triEvent
import reaktive.value.binding.map
import reaktive.value.forEach
import reaktive.value.now

class LFOsManager : ParameterControlList.Listener {
    private val observers = mutableMapOf<NamedParameterControl, Observer>()

    private val display = triEvent<NamedParameterControl, NumericalControlSpec, LFO>()
    private val remove = event<NamedParameterControl>()

    private val displayed = mutableSetOf<NamedParameterControl>()

    fun onDisplay(handler: (NamedParameterControl, NumericalControlSpec, LFO) -> Unit): Observer {
        for (param in displayed) {
            val lfo = lfoMap.getValue(param)
            val spec = param.spec.now as NumericalControlSpec
            handler(param, spec, lfo)
        }
        return display.stream.observe { _, (param, spec, lfo) -> handler(param, spec, lfo) }
    }

    fun onRemove(handler: (NamedParameterControl) ->Unit ) = remove.stream.observe { _, param -> handler(param) }

    private val lfoMap = mutableMapOf<NamedParameterControl, LFO>()

    fun getLFO(parameter: NamedParameterControl) = lfoMap[parameter]

    fun getLFOByName(parameter: String): LFO? {
        val param = lfoMap.keys.find { p -> p.name.now == parameter } ?: return null
        return getLFO(param)
    }

    override fun added(obj: NamedParameterControl, idx: Int) {
        addedControl(obj, obj.now)
    }

    private fun addedControl(obj: NamedParameterControl, ctrl: ParameterControl) {
        when (ctrl) {
            is UGenControl -> {
                observers[obj] = ctrl.expr.editor.result.forEach { expr ->
                    val lfo = expr.getLfo()
                    if (lfo == null) removeLFO(obj)
                    else {
                        lfo.initialize(this)
                        if (lfo.dependsOn(obj)) {
                            //TODO display infinite recursion warning somewhere
                            removeLFO(obj)
                        } else {
                            updateLFO(obj, lfo)
                        }
                    }
                } and ctrl.display.observe { _, _, doDisplay ->
                    if (doDisplay) {
                        val lfo = lfoMap[obj]
                        val spec = obj.spec.now
                        if (lfo != null && spec is NumericalControlSpec) {
                            displayed.add(obj)
                            display.fire(Triple(obj, spec, lfo))
                        }
                    } else {
                        if (displayed.remove(obj)) {
                            remove.fire(obj)
                        }
                    }
                }
            }

            is ValueControl -> {
                lfoMap[obj] = ConstantLFO(ctrl.value.map { v -> v.value })
                observers[obj] = ctrl.value.forEach { updated(obj) }
            }

            is EnvelopeControl -> {
                lfoMap[obj] = EnvelopeLFO(ctrl.points)
                observers[obj] = ctrl.update.stream.observe { _ -> updated(obj) }
            }

            else -> {}
        }
    }

    private fun removeLFO(obj: NamedParameterControl) {
        if (lfoMap.remove(obj) == null) return
        if (displayed.remove(obj)) {
            remove.fire(obj)
        }
        for ((param, lfo) in lfoMap) {
            if (lfo.dependsOn(obj)) {
                if (displayed.remove(param)) {
                    remove.fire(obj)
                }
            }
        }
    }

    private fun updateLFO(obj: NamedParameterControl, lfo: LFO) {
        lfoMap[obj] = lfo
        if (obj.now is UGenControl && lfo.isResolved()) {
            val spec = obj.spec.now
            if (spec is NumericalControlSpec) {
                displayed.add(obj)
                display.fire(Triple(obj, spec, lfo))
            }
        }
        updated(obj)
    }

    private fun updated(obj: NamedParameterControl) {
        for ((param, lfo) in lfoMap) {
            val ctrl = param.now
            if (ctrl is UGenControl && lfo.dependsOn(obj)) {
                val spec = param.spec.now
                if (spec is NumericalControlSpec) {
                    displayed.add(param)
                    display.fire(Triple(param, spec, lfo))
                }
                ctrl.update.fire()
            }
        }
    }

    override fun removed(obj: NamedParameterControl) {
        removeLFO(obj)
        observers.remove(obj)?.kill()
    }

    override fun reassignedControl(
        parameter: NamedParameterControl,
        oldControl: ParameterControl,
        newControl: ParameterControl,
    ) {
        removeLFO(parameter)
        observers.remove(parameter)?.kill()
        addedControl(parameter, newControl)
    }

    override fun changedSpec(parameter: NamedParameterControl, oldSpec: ControlSpec?, newSpec: ControlSpec?) {
        if (parameter.now !is UGenControl) return
        val lfo = lfoMap[parameter] ?: return
        if (newSpec is NumericalControlSpec) {
            if (lfo.isResolved()) {
                displayed.add(parameter)
                display.fire(Triple(parameter, newSpec, lfoMap[parameter]!!))
            }
        } else if (displayed.remove(parameter)) {
            remove.fire(parameter)
        }
    }
}