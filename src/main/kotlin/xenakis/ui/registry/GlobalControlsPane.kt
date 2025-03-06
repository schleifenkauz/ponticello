package xenakis.ui.registry

import fxutils.SubWindow
import fxutils.prompt.PredicateTextPrompt
import fxutils.registerShortcuts
import fxutils.styleClass
import hextant.context.Context
import hextant.context.createControl
import hextant.serial.makeRoot
import javafx.scene.layout.HBox
import javafx.scene.paint.Color.BLACK
import org.kordamp.ikonli.material2.Material2MZ
import reaktive.value.now
import xenakis.model.Logger
import xenakis.model.Logger.Category
import xenakis.model.Settings
import xenakis.model.registry.GlobalControls
import xenakis.sc.Identifier
import xenakis.sc.NumericalControlSpec
import xenakis.sc.editor.ControlSpecEditor
import xenakis.ui.actions.button
import xenakis.ui.controls.Knob
import xenakis.ui.impl.makeSubWindow
import xenakis.ui.launcher.XenakisLauncher.Companion.currentProject

class GlobalControlsPane(
    private val controls: GlobalControls,
    private val context: Context
) : HBox(), GlobalControlsView {
    init {
        children.add(Material2MZ.PLUS.button(action = "Add global control") { addControl() })
        controls.addView(this)
        styleClass("global-controls")
        registerShortcuts {
            on("Ctrl+PLUS") { addControl() }
        }
    }

    private fun addControl() {
        val name = PredicateTextPrompt("Control name", "") { name ->
            when {
                !Identifier.isValid(name) -> false
                controls.hasControl(name) -> false
                context[currentProject].busses.has("global_$name") -> false
                else -> true
            }
        }.showDialog(anchorNode = this) ?: return
        val editor = ControlSpecEditor(context)
        val defaultControlSpec = context[Settings].getDefaultControlSpec(name)
        editor.makeRoot()
        if (defaultControlSpec != null) {
            editor.setResult(defaultControlSpec)
        }
        val control = context.createControl(editor)
        val window = makeSubWindow(SubWindow.Type.Popup, control, "Configure global control", context)
        window.initOwner(scene.window)
        window.scene.fill = BLACK
        window.width = 800.0
        control.registerShortcuts {
            on("Ctrl+ENTER") {
                val spec = editor.result.now
                if (spec !is NumericalControlSpec) {
                    Logger.error("Only numerical control specs allowed for global controls", Category.GlobalControls)
                    return@on
                }
                controls.addControl(name, spec)
                window.hide()
            }
        }
        window.show()
    }

    override fun addedControl(control: GlobalControls.GlobalControl) {
        val knob = Knob(control.parameter, control.knobControl, control.spec, context, radius = 32.0)
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