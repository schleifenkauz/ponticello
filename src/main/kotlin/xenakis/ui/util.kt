package xenakis.ui

import bundles.Bundle
import hextant.core.Editor
import hextant.core.view.CompoundEditorControl
import javafx.beans.Observable
import javafx.beans.property.Property
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.event.ActionEvent
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.DialogPane
import javafx.scene.control.MenuItem
import javafx.scene.control.RadioButton
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.Region
import javafx.stage.Popup
import org.controlsfx.glyphfont.FontAwesome
import org.controlsfx.glyphfont.Glyph
import reaktive.Reactive
import java.time.Duration
import java.util.Optional
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

fun <N: Node> N.styleClass(vararg classes: String) = also { it.styleClass.addAll(classes) }

fun button(text: String, onAction: (ev: ActionEvent) -> Unit) = Button(text).also { btn -> btn.setOnAction(onAction) }

fun button(glyph: FontAwesome.Glyph, onAction: (ev: ActionEvent) -> Unit) =
    Button(null, Glyph("FontAwesome", glyph)).also { btn -> btn.setOnAction(onAction) }

fun showPopup(owner: Region, node: Node) = with(Popup()) {
    content.add(node)
    val coords = owner.localToScreen(0.0, owner.height)
    show(owner, coords.x, coords.y)
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

fun ClosedFloatingPointRange<Double>.reverseIfEmpty() = if (start > endInclusive) endInclusive..start else this

fun Double.format(accuracy: Int) = toString().let { s -> s.take(s.indexOf('.') + 1 + accuracy) }

fun <N: Node> N.antiScale(container: Node) = apply {
    val one = SimpleDoubleProperty(1.0)
    scaleXProperty().bind(one.divide(container.scaleXProperty()))
    scaleYProperty().bind(one.divide(container.scaleYProperty()))
}

fun Double.snap(grid: Double) = (this / grid).toInt() * grid

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

private class ReactiveCompoundEditorControl(
    editor: Editor<*>,
    arguments: Bundle,
    vararg repaintTriggers: Reactive,
    build: Vertical.(Bundle) -> Unit
) : CompoundEditorControl(editor, arguments, build) {
    init {
        repaintTriggers.forEach { trig ->
            trig.observe {
                root = createDefaultRoot()
            }
        }
    }
}

fun compoundControl(
    editor: Editor<*>,
    arguments: Bundle,
    vararg repaintTriggers: Reactive,
    build: CompoundEditorControl.Vertical.(Bundle) -> Unit
): CompoundEditorControl = ReactiveCompoundEditorControl(editor, arguments, *repaintTriggers, build = build)