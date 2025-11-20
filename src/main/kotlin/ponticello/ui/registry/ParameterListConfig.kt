package ponticello.ui.registry

import bundles.createBundle
import fxutils.actions.Action.IfNotApplicable
import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.asPopup
import fxutils.button
import fxutils.opacity
import fxutils.prompt.DetailPane
import fxutils.prompt.YesNoPrompt
import fxutils.showBelow
import hextant.context.createControl
import hextant.core.view.ChoiceEditorControl
import javafx.scene.Node
import javafx.scene.input.DataFormat
import javafx.scene.input.DragEvent
import javafx.scene.input.Dragboard
import javafx.scene.input.TransferMode
import javafx.scene.input.TransferMode.COPY_OR_MOVE
import org.kordamp.ikonli.materialdesign2.MaterialDesignD
import ponticello.impl.json
import ponticello.model.GlobalSettings
import ponticello.model.instr.ParameterDefObject
import ponticello.model.obj.withName
import ponticello.model.registry.reference
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

    override val dataFormat: DataFormat
        get() = ParameterDefObject.DATA_FORMAT

    override val canDuplicate: Boolean
        get() = true

    override fun acceptedTransferModes(dragboard: Dragboard): Array<TransferMode> =
        if (dragboard.hasContent(SERIALIZED_DATA_FORMAT)) COPY_OR_MOVE
        else if (dragboard.hasContent(dataFormat)) arrayOf(TransferMode.MOVE)
        else emptyArray()

    override fun getDroppedObjects(
        ev: DragEvent,
        targetView: ObjectListView<ParameterDefObject>
    ): List<ParameterDefObject> =
        when {
            ev.gestureSource !in targetView.getBoxes() || ev.acceptedTransferMode == TransferMode.COPY -> {
                val jsonString = ev.dragboard.getContent(SERIALIZED_DATA_FORMAT) as? String
                var obj = jsonString?.let { json.decodeFromString<ParameterDefObject>(it) } ?: return emptyList()
                if (ev.transferMode == TransferMode.COPY && ev.gestureSource in targetView.getBoxes()) {
                    val name = (targetView.source as ParameterDefList).availableName(obj.name.now)
                    obj = obj.copy().withName(name)
                }
                listOf(obj)
            }

            else -> super.getDroppedObjects(ev, targetView)
        }

    override fun configureDragboard(obj: ParameterDefObject, dragboard: Dragboard) {
        val jsonString = json.encodeToString(ParameterDefObject.serializer(), obj)
        dragboard.setContent(
            mapOf(
                ParameterDefObject.DATA_FORMAT to obj.reference(),
                SERIALIZED_DATA_FORMAT to jsonString
            )
        )
    }

    override fun getActions(box: ObjectBox<ParameterDefObject>): List<ContextualizedAction> =
        actions.withContext(box)

    override fun onRemoved(obj: ParameterDefObject) {
        observers.remove(obj)?.kill()
    }

    companion object {
        private val SERIALIZED_DATA_FORMAT = DataFormat("ponticello/parameter-def-serialized")

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
                            detailsPane.addItem("Allocate bus", editor.context.createControl(editor.allocateBus))
                        }

                        is BusControlSpecEditor -> {
                            detailsPane.addItem("Inline display", editor.context.createControl(editor.inlineDisplay))
                        }

                        is BufferControlSpecEditor -> {
                            detailsPane.addItem("Spectrogram", editor.context.createControl(editor.displaySpectrogram))
                            detailsPane.addItem("Inline display", editor.context.createControl(editor.inlineDisplay))
                        }

                        is BufferPositionControlSpecEditor -> {
                            detailsPane.addItem("Inline display", editor.context.createControl(editor.inlineDisplay))
                        }
                    }
                    val popup = detailsPane.asPopup()
                    detailsPane.children.add(button("Add to parameters list") { ev ->
                        val param = box.obj
                        val list = param.context[GlobalSettings].defaultParametersDefs
                        if (list.has(param.name.now)) {
                            val overwrite = YesNoPrompt(
                                "Parameter ${param.name.now} already exists in the list. Overwrite?"
                            ).showDialog(ev) ?: return@button
                            if (!overwrite) return@button
                        }
                        list.add(param.copy().withName(param.name.now))
                        popup.hide()
                    })
                    popup.scene.fill = DEFAULT_SCENE_FILL.opacity(0.5)
                    popup.showBelow(box.actionBar)
                }
            }
        }
    }
}