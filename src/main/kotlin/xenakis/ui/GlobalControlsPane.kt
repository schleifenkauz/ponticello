package xenakis.ui

import hextant.context.Context
import hextant.context.createControl
import hextant.fx.registerShortcuts
import hextant.serial.makeRoot
import javafx.scene.layout.HBox
import javafx.scene.paint.Color.BLACK
import reaktive.value.now
import xenakis.impl.Knob
import xenakis.model.GlobalControls
import xenakis.model.GlobalControlsView
import xenakis.sc.NumericalControlSpec
import xenakis.sc.editor.ParameterDefExpander

class GlobalControlsPane(
    private val controls: GlobalControls,
    private val context: Context
) : HBox(), GlobalControlsView {
    init {
        children.add(Icon.Add.button(radius = 32.0, action = "Add global control") { addControl() })
        controls.addView(this)
        styleClass("global-controls")
        registerShortcuts {
            on("Ctrl+PLUS") { addControl() }
        }
    }

    private fun addControl() {
        val editor = ParameterDefExpander(context)
        editor.makeRoot()
        val control = context.createControl(editor)
        val window = SubWindow(control, "Configure global control", context, SubWindow.Type.Modal)
        window.scene.fill = BLACK
        window.width = 1000.0
        control.registerShortcuts {
            on("Ctrl+ENTER") {
                val (name, spec) = editor.result.now
                if (spec !is NumericalControlSpec) {
                    alertError("Only numerical control specs allowed for global controls")
                    return@on
                }
                controls.addControl(name.text, spec)
                window.hide()
            }
        }
        window.show()
    }

    override fun addedControl(control: GlobalControls.GlobalControl) {
        val knob = Knob(control.knobControl, control.spec, radius = 32.0, context)
        knob.registerShortcuts {
            on("DELETE") { controls.removeControl(control) }
            on("F5") { controls.updateControlFromServer(control) }
        }
        children.add(children.size - 1, knob)
    }

    override fun removedControl(control: GlobalControls.GlobalControl) {
        children.removeIf { c -> c is Knob && c.control == control.knobControl }
    }
}