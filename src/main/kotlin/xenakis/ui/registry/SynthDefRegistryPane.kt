package xenakis.ui.registry

import fxutils.SubWindow
import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.prompt.SimpleSearchableListView
import fxutils.prompt.YesNoPrompt
import fxutils.setFixedWidth
import hextant.fx.initHextantScene
import javafx.application.Platform
import javafx.geometry.Point2D
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.input.DataFormat
import javafx.scene.paint.Color
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.MaterialDesignE
import reaktive.list.toReactiveList
import reaktive.value.binding.map
import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.impl.Logger
import xenakis.impl.async
import xenakis.impl.canSuperColliderTalkToMe
import xenakis.model.obj.CustomizableSynthDefObject
import xenakis.model.obj.NoSynthDef
import xenakis.model.obj.ReferencedSynthDefObject
import xenakis.model.obj.SynthDefObject
import xenakis.model.registry.GlobalSynthDefLib
import xenakis.model.registry.SynthDefRegistry
import xenakis.sc.Identifier
import xenakis.ui.impl.colorPicker
import xenakis.ui.registry.NamedObjectListView.ContentDisplay

class SynthDefRegistryPane(
    private val synthDefs: SynthDefRegistry,
) : ObjectRegistryPane<SynthDefObject>(synthDefs) {
    override val supportedModes: Set<ContentDisplay>
        get() = setOf(ContentDisplay.DetailsPane, ContentDisplay.SubWindow)

    init {
        setup()
    }

    override fun detailWindowIcon(obj: SynthDefObject): Ikon =
        if (obj is CustomizableSynthDefObject) Material2AL.CODE
        else MaterialDesignE.EYE

    override fun getContent(obj: SynthDefObject): Parent? = when (obj) {
        is CustomizableSynthDefObject -> {
            val title = obj.name.map { n -> "SynthDef $n" }
            ParameterizedObjectDefPane(registry.context, title, obj.parameters, obj.ugenGraph!!, obj::sync)
        }

        is ReferencedSynthDefObject -> {
            ParameterInfoPane(obj.parameters.toReactiveList())
        }

        is NoSynthDef -> null
    }

    override fun dataFormat(obj: SynthDefObject): DataFormat? = SynthDefObject.DATA_FORMAT

    override fun configureSubWindow(window: SubWindow) {
        window.scene.initHextantScene(registry.context, applyStyle = false)
        window.scene.fill = Color.BLACK
    }

    override fun sync() {
        synthDefs.syncAll()
        registry.save()
    }

    override fun addObject() {
        val globalLib = registry.context[GlobalSynthDefLib]
        val synthDefsFromGlobal = globalLib.getNames().map(AddInstrumentOption::SynthDefFromGlobalLib)
        val searchableList = AddInstrumentOptionListView(synthDefsFromGlobal)
        searchableList.enterText(searchText.text)
        val option = searchableList.showPopup(anchorNode = actionBar) ?: return
        createObject(option)
    }

    sealed interface AddInstrumentOption {
        data class NewSynthDef(val name: String) : AddInstrumentOption

        data class SynthDefFromGlobalLib(val synthDefName: String) : AddInstrumentOption
    }

    private inner class AddInstrumentOptionListView(
        options: List<AddInstrumentOption>,
    ) : SimpleSearchableListView<AddInstrumentOption>(options, "Add SynthDef") {
        override fun makeOption(text: String): AddInstrumentOption? {
            return if (Identifier.isValid(text) && !registry.has(text)) AddInstrumentOption.NewSynthDef(text)
            else null
        }

        override fun displayText(option: AddInstrumentOption): String = when (option) {
            is AddInstrumentOption.SynthDefFromGlobalLib -> "SynthDef: ${option.synthDefName}"
            else -> "<invalid>"
        }

        override fun extractText(option: AddInstrumentOption): String = when (option) {
            is AddInstrumentOption.NewSynthDef -> option.name
            is AddInstrumentOption.SynthDefFromGlobalLib -> option.synthDefName
        }
    }

    private fun createObject(option: AddInstrumentOption) {
        when (option) {
            is AddInstrumentOption.NewSynthDef -> {
                createSynthDef(option.name)?.let { def ->
                    registry.add(def)
                    listView.showContent(def)
                }
            }

            is AddInstrumentOption.SynthDefFromGlobalLib -> {
                val name = option.synthDefName
                val synthDef = registry.context[GlobalSynthDefLib].get(name) ?: return
                if (!registry.has(name) || YesNoPrompt("Overwrite SynthDef $name?")
                        .showDialog(anchorNode = actionBar) == true
                ) {
                    registry.overwrite(synthDef)
                    synthDef.sync()
                }
            }
        }
    }

    override fun addObject(name: String): SynthDefObject? {
        val obj = createSynthDef(name) ?: return null
        registry.add(obj)
        return obj
    }

    fun createSynthDef(name: String): SynthDefObject? {
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

    override fun getItemContent(obj: SynthDefObject): List<Node> = buildList {
        val colorPicker = colorPicker(obj.color)
        colorPicker.setFixedWidth(30.0)
        add(colorPicker)
    }

    override fun getActions(box: ObjectBox<SynthDefObject>): List<ContextualizedAction> = actions.withContext(box)

    companion object {
        private val actions = collectActions<ObjectBox<SynthDefObject>> {
            addAction("Save to global library") {
                icon(MaterialDesignE.EXPORT_VARIANT)
                applicableIf { box -> reactiveValue(box.obj is CustomizableSynthDefObject) }
                executes { box ->
                    saveToGlobalLibrary(box.obj as CustomizableSynthDefObject, box)
                }
            }
        }

        private fun saveToGlobalLibrary(obj: CustomizableSynthDefObject, anchorNode: ObjectBox<SynthDefObject>) {
            val globalLib = obj.context[GlobalSynthDefLib]
            val name = obj.name.now
            if (!globalLib.has(name) ||
                YesNoPrompt(
                    "Overwrite SynthDef $name in global library?",
                    default = true
                ).showDialog(anchorNode, offset = Point2D(anchorNode.width, 0.0)) == true
            ) {
                async {
                    globalLib.push(obj)
                    Platform.runLater {
                        Logger.confirm(
                            "Saved SynthDef '${obj.name.now}' to global library.",
                            Logger.Category.Instruments
                        )
                    }
                }
            }
        }
    }
}