package ponticello.ui.controls

import fxutils.*
import fxutils.prompt.SearchableListView
import fxutils.prompt.SimpleSearchableListView
import hextant.context.Context
import hextant.context.compoundEdit
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import ponticello.model.score.SoundProcess
import ponticello.model.score.controls.ParameterControl
import ponticello.ui.launcher.PonticelloApp.Companion.primaryStage
import reaktive.value.now

class MultiObjectControlPopup(
    private val selectedObjects: List<SoundProcess>,
) : SearchableListView<MultiObjectControlPopup.Option>("Select control") {
    override fun options(): List<Option> = selectedObjects
        .map { obj -> obj.controls.controlMap.keys }
        .reduceOrNull(Set<String>::intersect).orEmpty()
        .mapNotNull { name ->
            val controls = selectedObjects
                .map { obj -> obj.controls.getControl(name)!! }
            val commonType = controls
                .map { ctrl -> ControlType.getType(ctrl) }.distinct()
                .singleOrNull()
            if (commonType == null || !commonType.supportsDialogInput()) null
            else Option(name, commonType, controls)
        }

    override fun createCell(option: Option): Region = HBox(
        Label(option.controlName),
        infiniteSpace(),
        Label(option.controlType.toString()) styleClass "control-type-label",
    )

    override fun extractText(option: Option): String = option.controlName

    override fun makeOption(text: String): Option? {
        val availableTypes = ControlType.all.filter { t ->
            t.supportsDialogInput() && selectedObjects.all { obj ->
                val spec = obj.def.getSpec(text)?.now ?: return@makeOption null
                t.applicableOn(obj, spec)
            }
        }
        val type = SimpleSearchableListView(availableTypes, "Select control type")
            .showPopup(anchorNode = this) ?: return null
        return Option(text, type, null)
    }

    override val windowType: SubWindow.Type
        get() = SubWindow.Type.Prompt

    data class Option(val controlName: String, val controlType: ControlType<*>, val controls: List<ParameterControl>?)

    companion object {
        fun show(context: Context, selectedObjects: List<SoundProcess>) {
            val popup = MultiObjectControlPopup(selectedObjects)
            val option = popup.showPopup(context[primaryStage], null) ?: return

            @Suppress("UNCHECKED_CAST")
            val type = option.controlType as ControlType<ParameterControl>
            val name = option.controlName
            val specs = selectedObjects.map { obj ->
                obj.controls.get(name).spec.now ?: return@show
            }
            if (option.controls == null) {
                context.compoundEdit("Update $name") {
                    val controls = selectedObjects.map { obj ->
                        val spec = obj.def.getSpec(name)?.now ?: return@show
                        type.createInitialControl(obj, spec, null, name, anchorNode = null)
                    }
                    if (type.showDialogInput(name, specs, controls, context)) {
                        for ((obj, ctrl) in selectedObjects zip controls) {
                            if (obj.controls.has(name)) {
                                obj.controls.reassignControl(name, ctrl)
                            } else {
                                obj.controls.addControl(name, ctrl)
                            }
                        }
                    }
                }
            } else {
                type.showDialogInput(name, specs, option.controls, context)
            }
        }
    }
}