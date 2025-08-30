package ponticello.ui.misc

import fxutils.ChildrenAdder
import fxutils.centerChildren
import fxutils.children
import fxutils.hbox
import javafx.scene.control.Label
import ponticello.impl.Decimal
import ponticello.sc.NumericalControlSpec
import ponticello.ui.controls.Knob
import reaktive.value.ReactiveVariable


fun ChildrenAdder.item(name: String, children: ChildrenAdder.() -> Unit) {
    +hbox {
        spacing = 10.0
        centerChildren()
        children {
            +Label(name)
            children()
        }
    }
}

fun ChildrenAdder.knobItem(name: String, variable: ReactiveVariable<Decimal>, spec: NumericalControlSpec) {
    item(name) {
        +Knob(name, variable, spec)
    }
}

