package ponticello.sc.view

import bundles.Bundle
import fxutils.background
import fxutils.controls.SliderBar
import fxutils.infiniteSpace
import fxutils.pad
import fxutils.prompt.YesNoPrompt
import fxutils.setFixedWidth
import fxutils.undo.UndoManager
import fxutils.undo.VariableEdit
import hextant.codegen.ProvideImplementation
import hextant.context.ControlFactory
import hextant.core.view.EditorControl
import javafx.scene.Node
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import ponticello.impl.Decimal
import ponticello.model.ctx.PonticelloContext
import ponticello.model.score.controls.ExprControl
import ponticello.model.score.controls.UGenControl
import ponticello.sc.DecimalLiteral
import ponticello.sc.NumericalControlSpec
import ponticello.sc.editor.SliderExprEditor
import ponticello.ui.controls.SimpleNumericalControlSpecPrompt
import reaktive.Observer
import reaktive.value.now

class SliderEditorControl @ProvideImplementation(ControlFactory::class) constructor(
    private val editor: SliderExprEditor, arguments: Bundle
) : EditorControl<Node>(editor, arguments) {
    private val specObserver: Observer
    private var updateObserver: Observer? = null

    init {
        specObserver = editor.spec.observe { _, _, newSpec ->
            root = createSliderBar(newSpec)
        }
        pad(5.0)
        background = background(Color.gray(0.05))
    }

    private fun createSliderBar(spec: NumericalControlSpec): VBox {
        lateinit var bar: SliderBar<Decimal>
        val converter = spec.converter(updateRange = { min, max ->
            val extendRange = YesNoPrompt("Extend slider range", default = true)
                .showDialog(bar)
            if (extendRange == true) {
                val newSpec = spec.copy(min = DecimalLiteral(min), max = DecimalLiteral(max))
                updateSpec(newSpec)
                true
            } else false
        })
        bar = SliderBar(
            editor.value, "<value>", converter, style = SliderBar.Style.AlwaysValue,
            undoManager = context[UndoManager], updateActionDescription = "Update slider value"
        ).setFixedWidth(70.0)
        updateObserver?.kill()
        updateObserver = bar.updateFinished.observe { _ ->
            val ctx = context[PonticelloContext]
            if (ctx is PonticelloContext.Control) {
                when (val ctrl = ctx.control.now) {
                    is ExprControl -> ctrl.update.fire()
                    is UGenControl -> ctrl.update.fire()
                    else -> {}
                }
            }
        }
        bar.setOnMouseClicked { ev ->
            if (ev.clickCount == 2) {
                val newSpec = SimpleNumericalControlSpecPrompt(editor.spec.now)
                    .showDialog(this) ?: return@setOnMouseClicked
                updateSpec(newSpec)
            }
        }
        bar.background = background(Color.gray(0.05))
        return VBox(infiniteSpace(), bar.pad(5.0), infiniteSpace())
    }

    private fun updateSpec(newSpec: NumericalControlSpec) {
        VariableEdit.updateVariable(editor.spec, newSpec, context[UndoManager], "Extend slider range")
    }

    override fun createDefaultRoot(): VBox = createSliderBar(editor.spec.now)
}