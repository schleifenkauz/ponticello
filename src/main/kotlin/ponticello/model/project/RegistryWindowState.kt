package ponticello.model.project

import javafx.geometry.Dimension2D
import javafx.stage.Stage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.now
import ponticello.ui.registry.ObjectListView
import ponticello.ui.registry.ObjectRegistryPane

@Serializable
@SerialName("RegistryWindow")
class RegistryWindowState(override val reference: Reference) : WindowState() {
    private var selectedIndex: Int = -1
    private var displayMode: ObjectListView.DisplayMode? = null

    @Transient
    private var targetPane: ObjectRegistryPane<*>? = null

    override fun applyTo(window: Stage, defaultSize: Dimension2D?) {
        super.applyTo(window, defaultSize)
        val root = window.scene.root
        if (root is ObjectRegistryPane<*>) {
            targetPane = root
            val mode = displayMode
            if (mode != null && mode in root.listView.config.supportedModes) {
                root.listView.setMode(mode)
            }
            if (selectedIndex != -1) {
                root.listView.select(selectedIndex)
            }
        }
    }

    override fun saveFromTarget() {
        super.saveFromTarget()
        val targetPane = targetPane ?: return
        displayMode = targetPane.listView.mode.now
        selectedIndex = targetPane.listView.selectedIndex()
    }
}