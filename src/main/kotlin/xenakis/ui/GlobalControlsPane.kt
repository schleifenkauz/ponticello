package xenakis.ui

import hextant.context.Context
import hextant.context.createControl
import hextant.context.withoutUndo
import hextant.fx.registerShortcuts
import hextant.serial.makeRoot
import javafx.scene.layout.HBox
import javafx.scene.paint.Color.BLACK
import reaktive.value.now
import xenakis.impl.Knob
import xenakis.model.GlobalControls
import xenakis.model.GlobalControlsView
import xenakis.sc.Identifier
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
        showTextPrompt("Control name", "", context) { name ->
            if (!Identifier.isValid(name)) return@showTextPrompt false
            val editor = context.withoutUndo { ParameterDefExpander.expand(name, context) }
            editor.makeRoot()
            val control = context.createControl(editor)
            val window = SubWindow(control, "Configure global control", context, SubWindow.Type.Prompt)
            window.scene.fill = BLACK
            window.width = 1000.0
            control.registerShortcuts {
                on("Ctrl+ENTER") {
                    val (controlName, spec) = editor.result.now
                    if (spec !is NumericalControlSpec) {
                        alertError("Only numerical control specs allowed for global controls")
                        return@on
                    }
                    controls.addControl(controlName.text, spec)
                    window.hide()
                }
            }
            window.show()
            true
        }
    }

    override fun addedControl(control: GlobalControls.GlobalControl) {
        val knob = Knob(control.parameter, control.knobControl, control.spec, radius = 32.0, context)
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