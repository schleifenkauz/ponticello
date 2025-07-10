@file:Suppress("UsePropertyAccessSyntax", "UNCHECKED_CAST")

package ponticello.ui.impl

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
import javafx.stage.Window
import ponticello.impl.Decimal
import ponticello.impl.asY
import ponticello.model.obj.project
import ponticello.model.project.*
import ponticello.model.registry.NamedObject
import ponticello.model.registry.NamedObjectList
import ponticello.model.registry.ObjectReference
import ponticello.model.registry.reference
import ponticello.model.score.ScoreObject
import ponticello.model.score.ScoreObjectInstance
import ponticello.ui.actions.registerGlobalShortcuts
import ponticello.ui.launcher.PonticelloApp.Companion.primaryStage
import ponticello.ui.launcher.PonticelloLauncher.Companion.currentProject
import ponticello.ui.registry.ObjectRegistryPane
import reaktive.value.ReactiveString
import reaktive.value.ReactiveVariable
import reaktive.value.fx.asObservableValue
import reaktive.value.now

fun <T : NamedObject> Dragboard.getFrom(registry: NamedObjectList<T>, format: DataFormat): T? {
    val ref = getContent(format) as? ObjectReference<T> ?: return null
    ref.resolve(registry)
    return ref.get()
}

fun ScoreObjectInstance.verticalDist(y: Decimal) = when {
    y < this.y -> this.y - y
    y > y + height -> y - (this.y + this.height)
    else -> 0.0.asY
}

val KeyEvent.resizeMode: ScoreObject.ResizeMode?
    get() = resizeType(isShiftDown, isAltDown)

val MouseEvent.resizeMode: ScoreObject.ResizeMode?
    get() = resizeType(isShiftDown, isAltDown)

private fun resizeType(shift: Boolean, alt: Boolean) = when {
    shift && alt -> null
    shift -> ScoreObject.ResizeMode.Stretch
    alt -> ScoreObject.ResizeMode.DeepStretch
    else -> ScoreObject.ResizeMode.Regular
}

fun colorPicker(controlledVar: ReactiveVariable<Color>): ColorPicker {
    val picker = ColorPicker(controlledVar.now)
    picker.styleClass.add("button")
    picker.userData = controlledVar.observe { _, _, newColor -> picker.value = newColor }
    picker.valueProperty().addListener { _, _, newColor -> controlledVar.set(newColor) }
    return picker
}

fun <R> Prompt<R, *>.showDialog(context: Context) = showDialog(owner = context[primaryStage])

val DEFAULT_SCENE_FILL: Color = Color.web("#1d1d20")

fun makeSubWindow(
    root: Parent, title: String,
    context: Context, type: SubWindow.Type = SubWindow.Type.ToolWindow,
): SubWindow {
    val w = SubWindow(root, title, type)
    w.scene.fill = DEFAULT_SCENE_FILL
    w.scene.registerGlobalShortcuts(context)
    w.scene.initHextantScene(context)
    w.initOwner(context[primaryStage])
    return w
}

fun makeSubWindow(
    root: Parent, title: ReactiveString, context: Context,
    type: SubWindow.Type = SubWindow.Type.ToolWindow,
): SubWindow = makeSubWindow(root, title.get(), context, type).apply {
    titleProperty().bind(title.asObservableValue())
}

fun Context.makeToolWindow(
    root: Parent, title: String,
    defaultSize: Dimension2D? = null,
): SubWindow {
    val window = makeSubWindow(root, title, this, SubWindow.Type.ToolWindow)
    window.applySavedState(this, WindowState.Reference.ByTitle(title), defaultSize)
    return window
}

fun SubWindow.applySavedState(
    context: Context, reference: WindowState.Reference,
    defaultSize: Dimension2D?,
): SubWindow {
    if (context.hasProperty(currentProject)) {
        val interactionSettings = context.project[UI_STATE]
        val state = interactionSettings.getWindowState(reference) {
            if (scene.root is ObjectRegistryPane<*>) RegistryWindowState(reference)
            else RegularWindowState(reference)
        }
        state.applyTo(this, defaultSize)
    }
    return this
}

fun <T : NamedObject> makeSubWindow(obj: T, root: Parent, defaultSize: Dimension2D? = null): SubWindow {
    val windowRef = WindowState.Reference.ByDisplayedObject(obj.registry!!.objectType, obj.reference())
    return makeSubWindow(root, obj.name, obj.context)
        .applySavedState(obj.context, windowRef, defaultSize)
}

fun <W : Window> W.sceneFill(color: Color) = apply { scene.fill = color }