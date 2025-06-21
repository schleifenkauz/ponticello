package ponticello.ui.controls

import fxutils.*
import fxutils.prompt.SearchableListView
import hextant.context.Context
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

    data class Option(val controlName: String, val controlType: ControlType<*>, val controls: List<ParameterControl>)

    companion object {
        fun show(context: Context, selectedObjects: List<SoundProcess>) {
            val popup = MultiObjectControlPopup(selectedObjects)
            val option = popup.showPopup(context[primaryStage], null) ?: return

            @Suppress("UNCHECKED_CAST")
            val type = option.controlType as ControlType<ParameterControl>
            val specs = selectedObjects.map { obj ->
                obj.controls.get(option.controlName).spec.now ?: return //returns from outer function
            }
            type.showDialogInput(option.controlName, specs, option.controls, context)
        }
    }
}