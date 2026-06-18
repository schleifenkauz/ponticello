package ponticello.ui.registry

import fxutils.add
import fxutils.hbox
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import ponticello.model.instr.ParameterDefObject
import ponticello.sc.*
import reaktive.list.ReactiveList
import reaktive.list.fx.asObservableList
import reaktive.value.now

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
                        is BufferPositionControlSpec -> add(Label("bufpos"))
                        is AttackReleaseControlSpec -> add(Label("attack-release"))
                        is ExprControlSpec -> add(Label("expr"))
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
                        is ScoreObjectControlSpec -> add(Label("score-object"))
                    }
                }
        }
    }
}