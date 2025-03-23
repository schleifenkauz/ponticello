package xenakis.ui.registry

import fxutils.add
import fxutils.hbox
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import reaktive.list.ReactiveList
import reaktive.list.fx.asObservableList
import reaktive.value.now
import xenakis.model.obj.ParameterDefObject
import xenakis.sc.BufferControlSpec
import xenakis.sc.BusControlSpec
import xenakis.sc.GroupControlSpec
import xenakis.sc.NumericalControlSpec

class ParameterInfoPane(
    parameters: ReactiveList<ParameterDefObject>,
) : ListView<ParameterDefObject>(parameters.asObservableList()) {
    init {
        setCellFactory { _ -> ParameterCell() }
    }

    private class ParameterCell : ListCell<ParameterDefObject>() {
        override fun updateItem(item: ParameterDefObject?, empty: Boolean) {
            super.updateItem(item, empty)
            graphic =
                if (item == null || empty) null
                else hbox {
                    add(Label("${item.name.now}: ")) {
                        prefWidth = 100.0
                    }
                    when (val spec = item.spec.now) {
                        is BufferControlSpec -> add(Label("buf"))
                        is BusControlSpec -> add(Label("bus"))
                        is GroupControlSpec -> add(Label("group"))
                        is NumericalControlSpec -> {
                            add(Label("num, "))
                            add(Label("default = ${spec.defaultValue.text}, ")) {
                                prefWidth = 150.0
                            }
                            add(Label("min = ${spec.min.text}, ")) {
                                prefWidth = 100.0
                            }
                            add(Label("max = ${spec.max.text}, ")) {
                                prefWidth = 100.0
                            }
                            add(Label("step = ${spec.step.text}, ")) {
                                prefWidth = 100.0
                            }
                            add(Label("warp = ${spec.warp}")) {
                                prefWidth = 100.0
                            }
                        }
                    }
                }
        }
    }
}