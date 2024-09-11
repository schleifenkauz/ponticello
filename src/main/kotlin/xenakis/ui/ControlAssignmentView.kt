package xenakis.ui

import hextant.fx.shortcut
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.VBox
import xenakis.model.ParameterControl
import xenakis.model.SynthControls
import xenakis.model.SynthObject

class ControlAssignmentView(private val obj: SynthObject) : VBox(), SynthControls.View {
    private val editorByParameter = mutableMapOf<String, ControlAssignmentEditor>()
    private val editors = mutableListOf<ControlAssignmentEditor>()

    init {
        obj.controls.addView(this)
        prefWidth = 500.0
    }

    override fun addedControl(parameter: String, control: ParameterControl) {
        val spec = obj.getSpec(parameter)
        val editor = ControlAssignmentEditor(obj, parameter, spec)
        editor.setControl(control)
        editors.add(editor)

        editor.addEventFilter(KeyEvent.ANY) { ev ->
            if (ev.code == KeyCode.TAB) {
                if (ev.target != editor.getInputControl() || ev.eventType != KeyEvent.KEY_PRESSED) {
                    return@addEventFilter
                }
                ev.consume()
                var idx = editors.indexOf(editor)
                if ("TAB".shortcut.matches(ev)) {
                    while (++idx in editors.indices) {
                        if (editors[idx].focusInputControl()) {
                            break
                        }
                    }
                } else if ("Shift+TAB".shortcut.matches(ev) && idx - 1 in editors.indices) {
                    while (--idx in editors.indices) {
                        if (editors[idx].focusInputControl()) {
                            break
                        }
                    }
                }
            }
        }

        editorByParameter[parameter] = editor
        children.add(editor)
    }

    override fun removedControl(parameter: String, control: ParameterControl) {
        val editor = editorByParameter.remove(parameter)
        editors.remove(editor)
        children.remove(editor)
    }

    override fun reassignedControl(parameter: String, oldControl: ParameterControl, control: ParameterControl) {
        val editor = editorByParameter[parameter] ?: error("Editor for parameter '$parameter' not found")
        editor.setControl(control)
    }

    companion object {
        fun create(obj: SynthObject): ControlAssignmentView {
            val view = ControlAssignmentView(obj)
            return view
        }

    }
}