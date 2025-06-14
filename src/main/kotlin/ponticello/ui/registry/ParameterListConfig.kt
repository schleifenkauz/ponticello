package ponticello.ui.registry

import bundles.createBundle
import fxutils.actions.Action.IfNotApplicable
import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.asPopup
import fxutils.opacity
import fxutils.prompt.DetailPane
import fxutils.showBelow
import hextant.context.createControl
import hextant.core.view.ChoiceEditorControl
import javafx.scene.Node
import org.kordamp.ikonli.materialdesign2.MaterialDesignD
import ponticello.model.obj.ParameterDefObject
import ponticello.sc.ParameterType
import ponticello.sc.editor.BufferControlSpecEditor
import ponticello.sc.editor.BufferPositionControlSpecEditor
import ponticello.sc.editor.BusControlSpecEditor
import ponticello.sc.editor.NumericalControlSpecEditor
import ponticello.ui.impl.DEFAULT_SCENE_FILL
import reaktive.Observer
import reaktive.value.binding.map
import reaktive.value.now

open class ParameterListConfig : ListDisplayConfig<ParameterDefObject> {
    private val observers = mutableMapOf<ParameterDefObject, Observer>()

    override fun getHeaderContent(obj: ParameterDefObject): List<Node> {
        val specControl = ChoiceEditorControl(obj.specEditor, createBundle())
        specControl.canChoose = false
        return listOf(specControl)
    }

    override fun getActions(box: ObjectBox<ParameterDefObject>): List<ContextualizedAction> =
        actions.withContext(box)

    override fun onRemoved(obj: ParameterDefObject) {
        observers.remove(obj)?.kill()
    }

    companion object {
        private val actions = collectActions<ObjectBox<ParameterDefObject>> {
            addAction("Details") {
                icon(MaterialDesignD.DOTS_VERTICAL)
                enableWhen { box ->
                    box.obj.spec.map { spec ->
                        spec.type in ParameterType.regularTypes
                    }
                }
                ifNotApplicable(IfNotApplicable.Hide)
                executes { box, _ ->
                    val detailsPane = DetailPane(labelWidth = 110.0)
                    when (val editor = box.obj.specEditor.content.now) {
                        is NumericalControlSpecEditor -> {
                            detailsPane.addItem("Inline display", editor.context.createControl(editor.inlineDisplay))
                            detailsPane.addItem("Attack-release", editor.context.createControl(editor.attackRelease))
                        }

                        is BusControlSpecEditor -> {
                            detailsPane.addItem("Inline display", editor.context.createControl(editor.inlineDisplay))
                        }

                        is BufferControlSpecEditor -> {
                            detailsPane.addItem("Inline display", editor.context.createControl(editor.inlineDisplay))
                        }

                        is BufferPositionControlSpecEditor -> {
                            detailsPane.addItem("Inline display", editor.context.createControl(editor.inlineDisplay))
                        }
                    }
                    val popup = detailsPane.asPopup()
                    popup.scene.fill = DEFAULT_SCENE_FILL.opacity(0.5)
                    popup.showBelow(box.actionBar)
                }
            }
        }
    }
}