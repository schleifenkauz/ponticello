package ponticello.ui.controls

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.label
import fxutils.undo.UndoManager
import fxutils.undo.VariableEdit
import hextant.context.Context
import hextant.context.compoundEdit
import javafx.event.Event
import javafx.scene.Node
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
import ponticello.ui.registry.BufferSelectorPrompt
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
        val spec = namedControl.spec.now as? BufferControlSpec
            ?: return label("Invalid spec for ${namedControl.name.now} control")
        val editor = BufferSelector()
        editor.setFilter(spec.channels)
        editor.syncWith(control.sample)
        editor.initialize(namedControl.context)
        val selectorControl = ObjectSelectorControl(editor)
        return selectorControl
    }


    override fun createInitialControl(
        obj: ParameterizedObject, spec: ControlSpec?, oldControl: ParameterControl?, parameterName: String, ev: Event?,
    ): BufferControl {
        if (ev == null) return BufferControl(reactiveVariable(ObjectReference.none()))
        spec as BufferControlSpec
        val title = "Select '${parameterName}'"
        val selected = BufferSelectorPrompt(obj.context[BufferRegistry], title, channels = spec.channels)
            .showPopup(ev, initialOption = null)
        return BufferControl(reactiveVariable(selected?.reference() ?: ObjectReference.none()))
    }

    override fun supportsDialogInput(): Boolean = true

    override fun showDialogInput(
        parameterName: String, specs: List<ControlSpec>, controls: List<BufferControl>, context: Context,
    ): Boolean {
        val bufferSpecs = specs.filterIsInstance<BufferControlSpec>()
        if (bufferSpecs.size != specs.size) {
            Logger.warn("Some specs are not BufferControlSpec", Logger.Category.Score)
            return false
        }
        val channels = bufferSpecs.map { it.channels }.distinct().singleOrNull()
        if (channels == null) {
            Logger.warn("Some BufferControlSpec do not have the same number of channels", Logger.Category.Score)
            return false
        }
        val initialOption = controls.map { ctrl -> ctrl.sample.now }.distinct().singleOrNull()?.get()
        val newBuffer = BufferSelectorPrompt(context[BufferRegistry], "Choose $parameterName", channels)
            .showPopup(null, initialOption) ?: return false
        context.compoundEdit("Update $parameterName") {
            for (ctrl in controls) {
                VariableEdit.updateVariable(
                    ctrl.sample, newBuffer.reference(),
                    context[UndoManager], "Update $parameterName"
                )
            }
        }
        return true
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
    }
}