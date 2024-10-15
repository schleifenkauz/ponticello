@file:Suppress("UsePropertyAccessSyntax")

package xenakis.ui.impl

import hextant.context.Context
import hextant.context.withoutUndo
import hextant.fx.registerShortcuts
import hextant.serial.EditorRoot
import hextant.serial.snapshot
import javafx.beans.binding.Bindings
import javafx.beans.value.ObservableValue
import javafx.css.PseudoClass
import javafx.event.ActionEvent
import javafx.geometry.Bounds
import javafx.geometry.Insets
import javafx.geometry.Point2D
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.input.*
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.stage.Popup
import javafx.stage.Window
import org.controlsfx.glyphfont.FontAwesome
import org.controlsfx.glyphfont.Glyph
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import xenakis.impl.Decimal
import xenakis.impl.asY
import xenakis.model.obj.SuperColliderObject
import xenakis.model.score.ScoreObject
import xenakis.model.score.ScoreObjectInstance
import xenakis.sc.editor.CodeBlockEditor
import xenakis.ui.XenakisUI
import kotlin.math.pow
import kotlin.math.sqrt

operator fun Point2D.plus(other: Point2D) = Point2D(x + other.x, y + other.y)

operator fun Point2D.component1() = x
operator fun Point2D.component2() = y

infix fun Point2D.dist(p: Point2D) = sqrt((p.x - x).pow(2) + (p.y - y).pow(2))

fun <N : Node> N.styleClass(vararg classes: String) = also { it.styleClass.addAll(classes) }

infix fun <N : Node> N.styleClass(name: String) = also { it.styleClass.add(name) }

fun button(text: String = "", onAction: (ev: ActionEvent) -> Unit = {}) =
    Button(text).styleClass("sleek-button").also { btn -> btn.setOnAction(onAction) }

fun button(glyph: FontAwesome.Glyph, onAction: (ev: ActionEvent) -> Unit) =
    Button(null, Glyph("FontAwesome", glyph)).also { btn -> btn.setOnAction(onAction) }

fun textField(text: String = "", config: TextField.() -> Unit = {}) =
    TextField(text).styleClass("sleek-text-field").apply(config)

fun showPopup(owner: Node, node: Node) = popup(node).show(owner)

fun Popup.show(owner: Node) {
    val coords = owner.localToScreen(0.0, 0.0)
    show(owner, coords.x, coords.y)
}

inline fun popup(node: Node, block: Popup.() -> Unit = {}) = Popup().apply {
    content.add(node)
    isAutoHide = true
    block()
}

fun ToggleGroup.dontDeselectAll() {
    selectedToggleProperty().addListener { _, old, new ->
        if (new == null) old.isSelected = true
    }
}

fun <T, F> ObservableValue<out T>.map(f: (T) -> F): ObservableValue<F> =
    Bindings.createObjectBinding({ f(value) }, this)

fun <N : Region> N.alwaysHGrow() = also {
    maxWidth = Double.MAX_VALUE
    HBox.setHgrow(it, Priority.ALWAYS)
}

fun <N : Node> N.neverHGrow() = also { HBox.setHgrow(it, Priority.NEVER) }
fun <N : Node> N.alwaysVGrow() = also { VBox.setVgrow(it, Priority.ALWAYS) }

fun hspace(width: Double) = Region().apply { prefWidth = width }

fun infiniteSpace() = Region().alwaysHGrow()

fun <N : Node> N.centerChildren() = also {
    when (it) {
        is HBox -> it.alignment = Pos.CENTER_LEFT
        is VBox -> it.alignment = Pos.TOP_CENTER
        else -> {}
    }
}

fun Region.centerHorizontally(parent: Region) {
    layoutXProperty().bind(parent.widthProperty().subtract(widthProperty()).divide(2))
}

fun Node.setupDropArea(condition: (db: Dragboard) -> Boolean, onDrop: (ev: DragEvent) -> Unit) {
    addEventHandler(DragEvent.DRAG_OVER) { ev ->
        if (condition(ev.dragboard)) {
            ev.acceptTransferModes(*TransferMode.COPY_OR_MOVE)
            ev.consume()
        }
    }
    addEventHandler(DragEvent.DRAG_ENTERED) { ev ->
        if (condition(ev.dragboard)) {
            setPseudoClassState("drop-possible", true)
            ev.consume()
        }
    }
    addEventHandler(DragEvent.DRAG_EXITED) { ev ->
        if (condition(ev.dragboard)) {
            setPseudoClassState("drop-possible", false)
            ev.consume()
        }
    }
    addEventHandler(DragEvent.DRAG_DROPPED) { ev ->
        if (condition(ev.dragboard)) {
            try {
                onDrop(ev)
            } catch (ex: Exception) {
                System.err.println("Exception while dropping")
                ex.printStackTrace()
            }
            ev.isDropCompleted = true
            ev.consume()
        }
    }
}

fun Dragboard.hasFiles(extension: String) =
    hasFiles() && files.all { f -> f.extension.equals(extension, ignoreCase = true) }

fun Dragboard.hasFile(extension: String): Boolean = hasFiles(extension) && files.size == 1

fun solidBorder(fill: Color, width: Double = 1.0, radius: Double = 0.0) =
    Border(BorderStroke(fill, BorderStrokeStyle.SOLID, CornerRadii(radius), BorderWidths(width), Insets(-width)))

val Bounds.middleX get() = (minX + maxX) / 2
val Bounds.middleY get() = (minY + maxY) / 2

fun colorPicker(controlledVar: ReactiveVariable<Color>): ColorPicker {
    val picker = ColorPicker(controlledVar.now)
    picker.styleClass.add("button")
    picker.userData = controlledVar.observe { _, _, newColor -> picker.value = newColor }
    picker.valueProperty().addListener { _, _, newColor -> controlledVar.set(newColor) }
    return picker
}

fun label(text: ReactiveValue<String>): Label {
    val label = Label()
    label.textProperty().bind(text.asObservableValue())
    return label
}

fun plural(noun: String) = if (noun.endsWith("s")) "${noun}es" else "${noun}s"

fun Node.setPseudoClassState(name: String, value: Boolean) {
    pseudoClassStateChanged(PseudoClass.getPseudoClass(name), value)
}

fun ScoreObjectInstance.verticalDist(y: Decimal) = when {
    y < this.y -> this.y - y
    y > y + height -> y - (this.y + this.height)
    else -> 0.0.asY
}

fun background(color: Color) = Background(BackgroundFill(color, null, null))

fun Window.resize(width: Double, height: Double) {
    this.width = width
    this.height = height
}

fun Window.relocate(x: Double, y: Double) {
    this.x = x
    this.y = y
}

fun <R : Region> R.setFixedWidth(width: Double) = also { r ->
    r.prefWidth = width
    r.minWidth = width
    r.maxWidth = width
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

val Context.rootPane get() = get(XenakisUI).scoreView

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
