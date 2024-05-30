package xenakis.ui

import javafx.beans.binding.Bindings
import javafx.scene.control.Label
import javafx.scene.layout.VBox
import reaktive.list.ListChange
import reaktive.list.ReactiveList
import reaktive.value.binding.map
import reaktive.value.fx.asObservableValue
import xenakis.model.ParameterDefObject
import xenakis.sc.BufferControlSpec
import xenakis.sc.BusControlSpec
import xenakis.sc.ControlSpec
import xenakis.sc.NumericalControlSpec

class ParameterInfoPane(parameters: ReactiveList<ParameterDefObject>) : VBox() {
    init {
        for ((idx, parameter) in parameters.now.withIndex()) {
            displayParameter(parameter, idx)
        }
        parameters.observeList { ch ->
            when (ch) {
                is ListChange.Removed -> removedParameter(ch.index)
                is ListChange.Added -> displayParameter(ch.added, ch.index)
                is ListChange.Replaced -> {
                    removedParameter(ch.index)
                    displayParameter(ch.added, ch.index)
                }
            }
        }
    }

    private fun displayParameter(parameter: ParameterDefObject, idx: Int) {
        val label = Label()
        val name = parameter.name.map { name -> "$name: " }.asObservableValue()
        val info = parameter.spec.map { spec -> controlSpecInfo(spec) }.asObservableValue()
        label.textProperty().bind(Bindings.concat(name, info))
        children.add(idx, label)
    }

    private fun removedParameter(idx: Int) {
        children.removeAt(idx)
    }

    private fun controlSpecInfo(spec: ControlSpec): String = when (spec) {
        is BufferControlSpec -> "buffer"
        is BusControlSpec -> "bus"
        is NumericalControlSpec -> displayNumericalSpec(spec)
    }

    private fun displayNumericalSpec(spec: NumericalControlSpec) =
        "num: default=${spec.defaultValue.text}, range=${spec.min.text}..${spec.max.text}, " +
                "warp=${spec.warp}, step= ${spec.step}"

}