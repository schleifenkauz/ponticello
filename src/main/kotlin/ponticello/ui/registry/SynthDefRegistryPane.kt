package ponticello.ui.registry

import fxutils.prompt.YesNoPrompt
import fxutils.setFixedWidth
import javafx.event.Event
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.ScrollPane
import javafx.scene.input.DataFormat
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.MaterialDesignE
import ponticello.impl.canSuperColliderTalkToMe
import ponticello.model.obj.CustomizableSynthDefObject
import ponticello.model.obj.NoSynthDef
import ponticello.model.obj.ReferencedSynthDefObject
import ponticello.model.obj.SynthDefObject
import ponticello.model.registry.GlobalDefinitionLibrary
import ponticello.model.registry.SynthDefRegistry
import ponticello.ui.impl.colorPicker
import ponticello.ui.registry.ObjectListView.DisplayMode
import reaktive.list.toReactiveList

class SynthDefRegistryPane(
    private val synthDefs: SynthDefRegistry,
) : ParameterizedObjectDefRegistryPane<SynthDefObject>(synthDefs, GlobalDefinitionLibrary.synthDefs) {
    override val supportedModes: Set<DisplayMode>
        get() = setOf(DisplayMode.DetailsPane, DisplayMode.SubWindow)

    init {
        setup()
    }

    override fun detailWindowIcon(obj: SynthDefObject): Ikon =
        if (obj is CustomizableSynthDefObject) Material2AL.CODE
        else MaterialDesignE.EYE

    override fun getContent(obj: SynthDefObject, mode: DisplayMode): Parent? = when (obj) {
        is CustomizableSynthDefObject -> {
            val enableActions = mode == DisplayMode.SubWindow
            SynthDefObjectPane(obj, enableActions)
        }

        is ReferencedSynthDefObject -> ScrollPane(ParameterInfoPane(obj.parameters.toReactiveList()))
        is NoSynthDef -> null
    }

    override fun dataFormat(obj: SynthDefObject): DataFormat = SynthDefObject.DATA_FORMAT

    public override fun createNewObject(name: String, ev: Event?): SynthDefObject? {
        when {
            canSuperColliderTalkToMe && synthDefs.synthDescLibContains(name) -> {
                val reference = YesNoPrompt(
                    "SynthDef '$name' is already defined in the global SynthDescLib. " +
                            "Import SynthDef '$name' from SynthDescLib? A new SynthDef will be created otherwise.",
                    default = true
                ).showDialog(anchorNode = this) ?: return null
                return if (reference) ReferencedSynthDefObject.get(name)
                else CustomizableSynthDefObject.create(name)
            }

            else -> return CustomizableSynthDefObject.create(name)
        }
    }

    override fun getItemContent(obj: SynthDefObject): List<Node> = listOf(colorPicker(obj.color).setFixedWidth(30.0))
}