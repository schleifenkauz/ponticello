package xenakis.ui.controls

import fxutils.shortcut
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.VBox
import xenakis.model.obj.ParameterizedObject
import xenakis.model.score.ParameterControl
import xenakis.model.score.ParameterControls
import xenakis.sc.ControlSpec
import xenakis.ui.launcher.XenakisApp.Companion.primaryStage

class ControlAssignmentView(private val obj: ParameterizedObject) : VBox(), ParameterControls.Listener {
    private val editorByParameter = mutableMapOf<String, ControlAssignmentEditor>()
    private val editors = mutableListOf<ControlAssignmentEditor>()

    init {
        obj.controls.addListener(this)
    }

    override fun addedControl(parameter: String, control: ParameterControl) {
        if (obj.getSpec(parameter) == null) return
        val editor = ControlAssignmentEditor(obj, parameter)
        editor.setControl(control)
        editors.add(editor)
        navigateWithTab(editor)
        editorByParameter[parameter] = editor
        children.add(editor)
        if (scene != null && scene.window != obj.context[primaryStage]) scene.window.sizeToScene()
    }

    override fun removedControl(parameter: String, control: ParameterControl) {
        val editor = editorByParameter.remove(parameter) ?: return
        editors.remove(editor)
        children.remove(editor)
        if (scene != null && scene.window != obj.context[primaryStage]) scene.window.sizeToScene()
    }

    private fun navigateWithTab(editor: ControlAssignmentEditor) {
        editor.addEventFilter(KeyEvent.ANY) { ev ->
            if (ev.code == KeyCode.TAB) {
                if (ev.eventType != KeyEvent.KEY_PRESSED) {
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
    }

    override fun reassignedControl(parameter: String, oldControl: ParameterControl, control: ParameterControl) {
        val editor = editorByParameter[parameter] ?: error("Editor for parameter '$parameter' not found")
        editor.setControl(control)
    }

    override fun changedSpec(parameter: String, newSpec: ControlSpec) {
        val editor = editorByParameter[parameter] ?: return
        editor.setControl(obj.controls[parameter])
    }
}