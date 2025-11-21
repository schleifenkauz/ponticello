package ponticello.ui.actions

import fxutils.actions.Action
import fxutils.runFXWithTimeout
import ponticello.model.obj.project
import ponticello.model.project.scripts
import ponticello.ui.dock.AppLayout
import ponticello.ui.misc.CodePopup
import ponticello.ui.registry.ScriptRegistryPane
import ponticello.ui.registry.SimpleRegistrySelectorPrompt

object ScriptActions : Action.Collector<AppLayout>({
    addAction("Open script") {
        shortcut("Ctrl+K")
        executes { layout ->
            val registry = layout.context.project.scripts
            val script = SimpleRegistrySelectorPrompt(registry, "Open script...")
                .showDialog(layout.scene.window) ?: return@executes
            val pane = layout.get<ScriptRegistryPane>()
            pane.showContent(script)
        }
    }
    addAction("Evaluate expression") {
        shortcut("Ctrl+Shift+K")
        executes { layout ->
            val popup = CodePopup.get(layout.context)
            popup.show(layout.scene.window)
            runFXWithTimeout(50) {
                popup.requestFocus()
            }
        }
    }
})