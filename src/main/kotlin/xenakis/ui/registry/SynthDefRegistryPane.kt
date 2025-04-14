package xenakis.ui.registry

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.prompt.YesNoPrompt
import fxutils.setFixedWidth
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.input.DataFormat
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.MaterialDesignE
import reaktive.list.toReactiveList
import reaktive.value.reactiveValue
import xenakis.impl.canSuperColliderTalkToMe
import xenakis.model.obj.CustomizableSynthDefObject
import xenakis.model.obj.NoSynthDef
import xenakis.model.obj.ReferencedSynthDefObject
import xenakis.model.obj.SynthDefObject
import xenakis.model.registry.GlobalDefinitionLibrary
import xenakis.model.registry.SynthDefRegistry
import xenakis.ui.impl.colorPicker
import xenakis.ui.registry.NamedObjectListView.ContentDisplay

class SynthDefRegistryPane(
    private val synthDefs: SynthDefRegistry,
) : ParameterizedObjectDefRegistryPane<SynthDefObject>(synthDefs, GlobalDefinitionLibrary.synthDefs) {
    override val supportedModes: Set<ContentDisplay>
        get() = setOf(ContentDisplay.DetailsPane, ContentDisplay.SubWindow)

    init {
        setup()
    }

    override fun detailWindowIcon(obj: SynthDefObject): Ikon =
        if (obj is CustomizableSynthDefObject) Material2AL.CODE
        else MaterialDesignE.EYE

    override fun getContent(obj: SynthDefObject): Parent? = when (obj) {
        is CustomizableSynthDefObject -> SynthDefObjectPane(obj)
        is ReferencedSynthDefObject -> ParameterInfoPane(obj.parameters.toReactiveList())
        is NoSynthDef -> null
    }

    override fun dataFormat(obj: SynthDefObject): DataFormat = SynthDefObject.DATA_FORMAT

    public override fun createNewObject(name: String): SynthDefObject? {
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

    override fun getActions(box: ObjectBox<SynthDefObject>): List<ContextualizedAction> = actions.withContext(box)

    companion object {
        private val actions = collectActions<ObjectBox<SynthDefObject>> {
            addAction("Save to global library") {
                icon(MaterialDesignE.EXPORT_VARIANT)
                applicableIf { box -> reactiveValue(box.obj is CustomizableSynthDefObject) }
                executes { box ->
                    val obj = box.obj as CustomizableSynthDefObject
                    obj.context[GlobalDefinitionLibrary.synthDefs].saveToGlobalLib(obj, box)
                }
            }
        }
    }
}