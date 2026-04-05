package ponticello.ui.controls

import fxutils.SubWindow
import fxutils.actions.ContextualizedAction
import fxutils.button
import fxutils.centerChildren
import fxutils.controls.IntSpinner
import fxutils.prompt.InfoPrompt
import fxutils.prompt.nextToTarget
import fxutils.styleClass
import hextant.context.Context
import hextant.serial.EditorRoot
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.paint.Color
import ponticello.model.instr.BusObject
import ponticello.model.obj.BusReference
import ponticello.model.score.controls.NamedParameterControl
import ponticello.sc.BusControlSpec
import ponticello.sc.ControlSpec
import ponticello.sc.NumericalControlSpec
import ponticello.sc.Rate
import ponticello.sc.editor.BusSelector
import ponticello.sc.editor.ScExprExpander
import ponticello.sc.view.ObjectSelectorControl
import ponticello.ui.impl.makeSubWindow
import ponticello.ui.impl.sceneFill
import ponticello.ui.misc.CodePane
import reaktive.value.ReactiveVariable
import reaktive.value.binding.flatMap
import reaktive.value.binding.map
import reaktive.value.now
import reaktive.value.reactiveValue

fun busSelector(
    variable: ReactiveVariable<BusReference>,
    spec: ControlSpec?, context: Context,
): ObjectSelectorControl<BusObject> {
    val editor = BusSelector()
    if (spec is BusControlSpec) editor.setFilter(spec.rate, spec.channels)
    else editor.setFilter(rate = Rate.Control, channels = 1)
    editor.syncWith(variable)
    editor.initialize(context)
    return ObjectSelectorControl(editor)
}

fun busSelectorWithOffsetSpinner(
    reference: ReactiveVariable<BusReference>, offset: ReactiveVariable<Int>,
    spec: ControlSpec?, context: Context,
): HBox {
    val selector = busSelector(reference, spec, context)
    val spinner = IntSpinner(offset, 0, (reference.now.get()?.channels?.now ?: 1) - 1)
    val channels = reference.flatMap { ref -> ref.get()?.channels ?: reactiveValue(1) }
    spinner.userData = channels.observe { _, _, channels ->
        spinner.setMax(channels - 1)
    }
    return HBox(
        5.0,
        selector,
        Label("+").styleClass("keyword"),
        spinner
    ).centerChildren()
}

fun makeCodePaneWindow(
    root: EditorRoot<ScExprExpander>, context: Context,
    namedControl: NamedParameterControl, actions: List<ContextualizedAction>,
): SubWindow {
    val pane = CodePane(
        root, extraActions = actions,
        actionBarAlignment = Pos.BOTTOM_RIGHT, ownWindow = true
    )
    val title = namedControl.name.map { name -> "Code for control '$name'" }
    val window = makeSubWindow(pane, title, context)
    window.sceneFill(Color.BLACK)
    window.minWidth = 100.0
    window.minHeight = 100.0
    return window
}

fun missingSpecOptionsBar(control: NamedParameterControl): HBox = HBox(
    5.0,
    Label("Unresolved spec"),
    button("Sync") { ev ->
        val success = control.useSpecFromDefinition()
        if (!success) {
            InfoPrompt("No spec found in '${control.parentObject.getInstrument().name.now}'")
                .showDialog(ev.nextToTarget())
        }
    },
    button("Custom") { ev ->
        val spec = NumericalControlSpecPrompt(
            control.name.now, control.parentObject, NumericalControlSpec.DEFAULT,
            "Provide custom specification"
        ).showDialog(ev.nextToTarget()) ?: return@button
        control.setCustomSpec(spec)
    }
).centerChildren()

