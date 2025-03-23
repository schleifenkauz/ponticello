package xenakis.ui.registry

import fxutils.prompt.SimpleSearchableListView
import hextant.context.Context
import javafx.geometry.Point2D
import javafx.scene.layout.Region
import javafx.stage.Window
import reaktive.value.now
import xenakis.model.obj.ParameterDefObject
import xenakis.model.obj.ParameterizedObject
import xenakis.sc.ControlSpec
import xenakis.sc.Identifier
import xenakis.sc.NumericalControlSpec
import xenakis.sc.ParameterType
import xenakis.ui.controls.ControlSpecPrompt
import xenakis.ui.controls.NumericalControlSpecPrompt
import xenakis.ui.launcher.XenakisApp.Companion.primaryStage

class SearchableParameterDefListView(
    options: List<ParameterDefObject>, title: String,
    private val parentObject: ParameterizedObject?,
    private val ownerWindow: Window,
    private val anchor: Point2D,
    private val fixedParameterType: ParameterType? = null,
) : SimpleSearchableListView<ParameterDefObject>(options, title) {
    override fun extractText(option: ParameterDefObject): String = option.name.now

    override fun displayText(option: ParameterDefObject): String = option.simpleString()

    override fun makeOption(text: String): ParameterDefObject? {
        if (!Identifier.isValid(text)) return null
        Thread.sleep(50)
        val type = fixedParameterType ?: SimpleSearchableListView(ParameterType.regularTypes, "Parameter type")
            .showPopup(anchor, ownerWindow, initialOption = ParameterType.Numerical) ?: return null
        val prompt = ControlSpecPrompt.create(text, parentObject, type) ?: return null
        val spec = prompt.showDialog(ownerWindow, anchor) ?: return null
        return ParameterDefObject(text, spec)
    }

    fun showPopup(): ParameterDefObject? = showPopup(anchor, ownerWindow)
}