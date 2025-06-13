package ponticello.ui.controls

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.actions.detailsAction
import fxutils.controls.CheckBox
import fxutils.label
import fxutils.opacity
import fxutils.undo.UndoManager
import javafx.scene.Node
import javafx.scene.layout.Region
import org.kordamp.ikonli.evaicons.Evaicons
import ponticello.impl.Logger
import ponticello.model.obj.AllocatedBufferObject
import ponticello.model.obj.BufferReference
import ponticello.model.obj.ParameterizedObject
import ponticello.model.obj.SampleObject
import ponticello.model.registry.BufferRegistry
import ponticello.model.registry.ObjectReference
import ponticello.model.registry.reference
import ponticello.model.score.ParameterControlList
import ponticello.model.score.controls.BufferControl
import ponticello.model.score.controls.ParameterControl
import ponticello.sc.BufferControlSpec
import ponticello.sc.ControlSpec
import ponticello.sc.editor.BufferSelector
import ponticello.sc.view.ObjectSelectorControl
import ponticello.ui.impl.DEFAULT_SCENE_FILL
import ponticello.ui.registry.SearchableBufferListView
import ponticello.ui.score.ScoreObjectView
import reaktive.value.binding.flatMap
import reaktive.value.now
import reaktive.value.reactiveVariable

data object BufferControlType : ControlType<BufferControl>() {
    override fun applicableOn(obj: ParameterizedObject, spec: ControlSpec): Boolean = spec is BufferControlSpec

    override fun createDetailInput(
        namedControl: ParameterControlList.NamedParameterControl,
        control: BufferControl,
        view: ScoreObjectView?,
    ): Node = createSimpleInput(namedControl, control)

    override fun createSimpleInput(
        namedControl: ParameterControlList.NamedParameterControl,
        control: BufferControl,
    ): Node {
        val spec = namedControl.spec.now as? BufferControlSpec ?: return label("Invalid spec for ${namedControl.name.now} control")
        val editor = BufferSelector()
        editor.setFilter(spec.channels)
        editor.syncWith(control.sample)
        editor.initialize(namedControl.context)
        val selectorControl = ObjectSelectorControl(editor)
        return selectorControl
    }


    override fun createInitialControl(
        obj: ParameterizedObject,
        spec: ControlSpec?,
        oldControl: ParameterControl,
        namedControl: ParameterControlList.NamedParameterControl,
        anchorNode: Region,
    ): BufferControl {
        spec as BufferControlSpec
        val display = reactiveVariable(spec.isPlayBufSource)
        val title = "Select '${namedControl.name.now}'"
        val selected = SearchableBufferListView(obj.context[BufferRegistry], title, channels = spec.channels)
            .showPopup(anchorNode, initialOption = null)
        return BufferControl(reactiveVariable(selected?.reference() ?: ObjectReference.none()), display)
    }

    override fun actions(
        namedControl: ParameterControlList.NamedParameterControl,
        control: BufferControl,
        view: ScoreObjectView?,
    ): List<ContextualizedAction> = actions.withContext(control)

    override fun toString(): String = "Buf"

    private val actions = collectActions<BufferControl> {
        addAction("View contents") {
            icon(Evaicons.ACTIVITY)
            enableWhen { ctrl -> ctrl.sample.flatMap(BufferReference::isResolved) }
            executes { ctrl ->
                when (val buf = ctrl.sample.now.get()) {
                    is SampleObject -> buf.showSpectrogram()
                    is AllocatedBufferObject -> buf.plotBuffer()
                    null -> Logger.warn("Unresolved buffer ${ctrl.sample.now}", Logger.Category.Buffers)
                }
            }
        }
        add(detailsAction(sceneFill = DEFAULT_SCENE_FILL.opacity(0.5)) { ctrl ->
            CheckBox(ctrl.display)
                .setupUndo(ctrl.context[UndoManager], variableDescription = "Display")
                .named("Display")
        })
    }
}