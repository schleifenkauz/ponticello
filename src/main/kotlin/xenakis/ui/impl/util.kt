@file:Suppress("UsePropertyAccessSyntax")

package xenakis.ui.impl

import fxutils.SubWindow
import fxutils.prompt.Prompt
import fxutils.registerShortcuts
import hextant.context.Context
import hextant.context.withoutUndo
import hextant.serial.EditorRoot
import hextant.serial.snapshot
import javafx.scene.Parent
import javafx.scene.control.ColorPicker
import javafx.scene.input.DataFormat
import javafx.scene.input.Dragboard
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import javafx.scene.paint.Color
import reaktive.value.ReactiveVariable
import reaktive.value.now
import xenakis.impl.Decimal
import xenakis.impl.asY
import xenakis.model.obj.SuperColliderObject
import xenakis.model.registry.NamedObject
import xenakis.model.registry.ObjectRegistry
import xenakis.model.score.ScoreObject
import xenakis.model.score.ScoreObjectInstance
import xenakis.sc.editor.CodeBlockEditor
import xenakis.ui.launcher.XenakisApp.Companion.primaryStage
import xenakis.ui.launcher.XenakisMainActivity

fun <T : NamedObject> Dragboard.getFrom(registry: ObjectRegistry<T>, format: DataFormat): T {
    val name = getContent(format) as String
    return registry.get(name)
}

fun ScoreObjectInstance.verticalDist(y: Decimal) = when {
    y < this.y -> this.y - y
    y > y + height -> y - (this.y + this.height)
    else -> 0.0.asY
}

val KeyEvent.resizeType: ScoreObject.ResizeType?
    get() = resizeType(isShiftDown, isAltDown)

val MouseEvent.resizeType: ScoreObject.ResizeType?
    get() = resizeType(isShiftDown, isAltDown)

private fun resizeType(shift: Boolean, alt: Boolean) = when {
    shift && alt -> ScoreObject.ResizeType.DeepStretch
    shift -> ScoreObject.ResizeType.Stretch
    alt -> null
    else -> ScoreObject.ResizeType.Regular
}

val Context.rootPane get() = get(XenakisMainActivity).scoreView

fun SubWindow.registerSyncShortcuts(obj: SuperColliderObject, code: EditorRoot<CodeBlockEditor>) {
    scene.registerShortcuts {
        on("Ctrl+S") {
            code.editor.context.withoutUndo {
                code.editor.snapshot().reconstructObject(code.editor)
            }
            obj.sync()
        }
        on("Ctrl+Shift+S") {
            obj.sync()
            hide()
        }
    }
}

fun colorPicker(controlledVar: ReactiveVariable<Color>): ColorPicker {
    val picker = ColorPicker(controlledVar.now)
    picker.styleClass.add("button")
    picker.userData = controlledVar.observe { _, _, newColor -> picker.value = newColor }
    picker.valueProperty().addListener { _, _, newColor -> controlledVar.set(newColor) }
    return picker
}

fun <R> Prompt<R, *>.showDialog(context: Context) = showDialog(owner = context[primaryStage])

fun makeSubWindow(
    root: Parent, title: String,
    context: Context, type: SubWindow.Type = SubWindow.Type.ToolWindow
): SubWindow {
    val w = SubWindow(root, title, type)
    w.initOwner(context[primaryStage])
    return w
}