@file:Suppress("UsePropertyAccessSyntax")

package xenakis.ui.impl

import fxutils.SubWindow
import fxutils.prompt.Prompt
import hextant.context.Context
import hextant.fx.initHextantScene
import javafx.geometry.Dimension2D
import javafx.scene.Parent
import javafx.scene.control.ColorPicker
import javafx.scene.input.DataFormat
import javafx.scene.input.Dragboard
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import javafx.scene.paint.Color
import reaktive.value.ReactiveString
import reaktive.value.ReactiveVariable
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import xenakis.impl.Decimal
import xenakis.impl.asY
import xenakis.model.project.RegistryWindowState
import xenakis.model.project.RegularWindowState
import xenakis.model.project.UI_STATE
import xenakis.model.project.get
import xenakis.model.registry.NamedObject
import xenakis.model.registry.ObjectRegistry
import xenakis.model.score.ScoreObject
import xenakis.model.score.ScoreObjectInstance
import xenakis.ui.launcher.XenakisApp.Companion.primaryStage
import xenakis.ui.launcher.XenakisLauncher.Companion.currentProject
import xenakis.ui.launcher.XenakisMainActivity
import xenakis.ui.registry.ObjectRegistryPane

fun <T : NamedObject> Dragboard.getFrom(registry: ObjectRegistry<T>, format: DataFormat): T? {
    val name = getContent(format) as String
    return registry.getOrNull(name)
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
    context: Context, type: SubWindow.Type = SubWindow.Type.ToolWindow,
): SubWindow {
    val w = SubWindow(root, title, type)
    w.initOwner(context[primaryStage])
    return w
}

fun makeSubWindow(
    root: Parent, title: ReactiveString,
    context: Context, type: SubWindow.Type = SubWindow.Type.ToolWindow,
): SubWindow = makeSubWindow(root, title.get(), context, type).apply {
    titleProperty().bind(title.asObservableValue())
}

fun Context.makeToolWindow(
    root: Parent, title: String,
    defaultSize: Dimension2D? = null,
): SubWindow {
    val window = SubWindow(root, title, SubWindow.Type.ToolWindow)
    window.scene.initHextantScene(this)
    window.initOwner(this[primaryStage])
    if (hasProperty(currentProject)) {
        val interactionSettings = this[currentProject][UI_STATE]
        val state = interactionSettings.windowStates.getOrPut(title) {
            if (root is ObjectRegistryPane<*>) RegistryWindowState()
            else RegularWindowState()
        }
        state.applyTo(window, defaultSize)
    }
    return window
}