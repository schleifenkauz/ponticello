@file:Suppress("UsePropertyAccessSyntax")

package xenakis.ui

import javafx.beans.Observable
import javafx.beans.property.Property
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.css.PseudoClass
import javafx.event.ActionEvent
import javafx.geometry.Bounds
import javafx.geometry.Pos
import javafx.scene.Cursor
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.input.DragEvent
import javafx.scene.input.Dragboard
import javafx.scene.input.TransferMode
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
import java.util.*
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

fun <T> Optional<T>.getOrNull(): T? = orElse(null)

fun ToggleGroup.addRadioButton(text: String, controlledNode: Node? = null) = RadioButton(text).also { btn ->
    btn.toggleGroup = this
    userData = text
    btn.selectedProperty().addListener { _, _, selected ->
        if (selected && controlledNode != null && btn.scene != null) {
            showPopup(btn, controlledNode)
        }
    }
    controlledNode?.disableProperty()?.bind(selectedToggleProperty().isEqualTo(btn).not())
}

fun DialogPane.setDefaultButton(type: ButtonType, disable: ObservableValue<Boolean>) {
    val btn = lookupButton(type) as Button
    btn.disableProperty().bind(disable)
    btn.isDefaultButton = true
}

fun <N : Node> N.styleClass(vararg classes: String) = also { it.styleClass.addAll(classes) }

infix fun <N : Node> N.styleClass(name: String) = also { it.styleClass.add(name) }

fun button(text: String = "", onAction: (ev: ActionEvent) -> Unit) =
    Button(text).also { btn -> btn.setOnAction(onAction) }

fun button(glyph: FontAwesome.Glyph, onAction: (ev: ActionEvent) -> Unit) =
    Button(null, Glyph("FontAwesome", glyph)).also { btn -> btn.setOnAction(onAction) }

fun textField(text: String = "", config: TextField.() -> Unit) = TextField(text).apply(config)

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

private fun <T, P : Property<T>> bindingImpl(property: P, observables: Array<out Observable>, compute: () -> T): P {
    property.value = compute()
    for (obs in observables) {
        obs.addListener { property.value = compute() }
    }
    return property
}

fun <T> binding(vararg observables: Observable, compute: () -> T): ObservableValue<T> =
    bindingImpl(SimpleObjectProperty(), observables, compute)

fun <T, F> ObservableValue<out T>.map(f: (T) -> F): ObservableValue<F> = binding(this) { f(value) }

fun menuItem(text: String, action: () -> Unit) = MenuItem(text).also { item ->
    item.setOnAction { ev ->
        action()
        ev.consume()
    }
}

fun <N : Node> N.alwaysHGrow() = also { HBox.setHgrow(it, Priority.ALWAYS) }
fun <N : Node> N.neverHGrow() = also { HBox.setHgrow(it, Priority.NEVER) }
fun <N : Node> N.alwaysVGrow() = also { VBox.setVgrow(it, Priority.ALWAYS) }

fun hspace(width: Double) = Region().apply { prefWidth = width }

fun infiniteSpace() = Region().alwaysHGrow()

fun <N : Node> N.centerChildrenVertically() = also {
    when (it) {
        is HBox -> it.alignment = Pos.CENTER_LEFT
        is VBox -> it.alignment = Pos.CENTER_LEFT
        else -> {}
    }
}

fun ClosedFloatingPointRange<Double>.reverseIfEmpty() = if (start > endInclusive) endInclusive..start else this

fun accuracy(delta: Double) = ceil(-log10(delta).coerceAtMost(0.0)).toInt()

fun Double.format(accuracy: Int) = toString().let { s ->
    if (accuracy == 0) s.take(s.indexOf('.'))
    else s.take(s.indexOf('.') + 1 + accuracy)
}

fun Double.round(accuracy: Int): Double {
    val factor = 10.0.pow(accuracy)
    return (this * factor).roundToInt() / factor
}

fun <N : Node> N.antiScale(container: Node) = apply {
    val one = SimpleDoubleProperty(1.0)
    scaleXProperty().bind(one.divide(container.scaleXProperty()))
    scaleYProperty().bind(one.divide(container.scaleYProperty()))
}

fun Region.centerHorizontally(parent: Region) {
    layoutXProperty().bind(parent.widthProperty().subtract(widthProperty()).divide(2))
}

fun Double.snap(grid: Double): Double = if (grid == 0.0 || grid.isNaN()) this else (this / grid).roundToInt() * grid

fun timeCode(t: Double, accuracy: Int): String {
    var seconds = t.toInt()
    val milliseconds = ((t - seconds) * 10.0.pow(accuracy)).toInt()
    val minutes = seconds / 60
    seconds %= 60
    return when {
        accuracy == 0 && minutes == 0 -> "$seconds"
        accuracy == 0 -> String.format("%d:%02d", minutes, seconds)
        minutes == 0 -> String.format("%d,%0${accuracy}d", seconds, milliseconds)
        else -> String.format("%d:%02d,%0${accuracy}d", minutes, seconds, milliseconds)
    }
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
            onDrop(ev)
            ev.isDropCompleted = true
            ev.consume()
        }
    }
}

fun Dragboard.hasFiles(extension: String) = hasFiles() && files.all { f -> f.extension == extension }

fun Dragboard.hasFile(extension: String): Boolean = hasFiles(extension) && files.size == 1

fun solidBorder(fill: Color, width: Double = 1.0, radius: Double = 0.0) =
    Border(BorderStroke(fill, BorderStrokeStyle.SOLID, CornerRadii(radius), BorderWidths(width)))

val Bounds.middleY get() = (minY + maxY) / 2

val Cursor.resizeFromLeft get() = this in setOf(Cursor.W_RESIZE, Cursor.NW_RESIZE, Cursor.SW_RESIZE)
val Cursor.resizeFromTop get() = this in setOf(Cursor.N_RESIZE, Cursor.NE_RESIZE, Cursor.NW_RESIZE)

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

fun Region.verticalDist(y: Double) = when {
    y < layoutY -> layoutY - y
    y > layoutY + height -> y - (layoutY + height)
    else -> 0.0
}

fun ToggleButton.toggle() {
    isSelected = !isSelected
}

fun background(color: Color) = Background(BackgroundFill(color, null, null))

fun Window.resize(width: Double, height: Double) {
    this.width = width
    this.height = height
}

fun <R : Region> R.setPreferredWidth(width: Double) = also { r -> r.prefWidth = width }