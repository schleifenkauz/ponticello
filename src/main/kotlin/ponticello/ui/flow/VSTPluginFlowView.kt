package ponticello.ui.flow

import bundles.createBundle
import fxutils.*
import fxutils.actions.button
import fxutils.prompt.SimpleSearchableListView
import fxutils.prompt.SimpleTextPrompt
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import org.kordamp.ikonli.codicons.Codicons
import org.kordamp.ikonli.material2.Material2MZ
import ponticello.model.flow.VSTPluginFlow
import ponticello.model.flow.VSTPlugins
import ponticello.sc.view.ObjectSelectorControl
import reaktive.value.now

class VSTPluginFlowView(private val flow: VSTPluginFlow) : VBox() {
    init {
        val busSelector = ObjectSelectorControl(flow.busSelector, createBundle())
            .widthAtLeast(100.0)

        val pluginSelectorBtn = button(flow.pluginName, style = "selector-button") { ev ->
            val options = VSTPlugins.availablePlugins(flow.context).toList()
            val newPluginName = SimpleSearchableListView(options, "Select VST Plugin").showPopup(ev) ?: return@button
            flow.loadPlugin(newPluginName)
        }
        val saveGlobalPresetBtn = Material2MZ.SAVE.button("Save global preset", "medium-icon-button") { ev ->
            val name = SimpleTextPrompt("Preset name", "").showDialog(ev) ?: return@button
            flow.saveGlobalPreset(name)
        }
        val loadGlobalPresetBtn = Codicons.FOLDER_OPENED.button("Load global preset", "medium-icon-button") { ev ->
            val options = VSTPlugins.globalPresetList(flow.context, flow.pluginName.now)
            val presetName = SimpleSearchableListView(options, "Select preset")
                .showPopup(ev) ?: return@button
            flow.loadGlobalPreset(presetName)
        }
        children.addAll(
            HBox(5.0, label("Target bus: "), busSelector).centerChildren().pad(3.0),
            HBox(
                5.0, pluginSelectorBtn, saveGlobalPresetBtn, loadGlobalPresetBtn
            ).centerChildren().pad(3.0)
        )
        children.add(VSTPluginParameterMappingsPane(flow))
    }
}