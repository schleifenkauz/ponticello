package ponticello.ui.flow

import fxutils.actions.button
import fxutils.button
import fxutils.centerChildren
import fxutils.prompt.DetailPane
import fxutils.prompt.SimpleSearchableListView
import fxutils.prompt.SimpleTextPrompt
import javafx.scene.layout.HBox
import org.kordamp.ikonli.codicons.Codicons
import org.kordamp.ikonli.material2.Material2MZ
import ponticello.model.flow.VSTPluginFlow
import ponticello.model.flow.VSTPlugins
import reaktive.value.now

class VSTPluginFlowView(private val flow: VSTPluginFlow) : DetailPane(labelWidth = 120.0) {
    init {
        val pluginSelectorBtn = button(flow.pluginName) { ev ->
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
        addItem("Plugin", HBox(5.0, pluginSelectorBtn, saveGlobalPresetBtn, loadGlobalPresetBtn).centerChildren())
        children.add(VSTPluginParameterMappingsPane(flow))
    }
}