package ponticello.ui.actions

import bundles.set
import fxutils.actions.Action
import fxutils.awaitFx
import fxutils.pad
import fxutils.registerShortcuts
import fxutils.runFXWithTimeout
import fxutils.solidBorder
import hextant.context.SelectionDistributor
import hextant.context.createControl
import hextant.context.extend
import hextant.core.editor.defaultState
import hextant.fx.initHextantScene
import hextant.serial.Root
import javafx.application.Platform
import javafx.scene.layout.BorderPane
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import javafx.stage.Popup
import ponticello.model.obj.project
import ponticello.model.project.scripts
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.eval
import ponticello.sc.code
import ponticello.sc.editor.ScExprExpander
import ponticello.ui.dock.AppLayout
import ponticello.ui.impl.sceneFill
import ponticello.ui.misc.ResultPopup
import ponticello.ui.registry.ScriptRegistryPane
import ponticello.ui.registry.SimpleRegistrySelectorPrompt
import reaktive.value.now

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
            val editor = ScExprExpander().defaultState()
            val selector = SelectionDistributor.newInstance()
            val ctx = layout.context.extend {
                set(SelectionDistributor, selector)
            }
            editor.initialize(ctx, null, Root, null)

            val control = ctx.createControl(editor)
            val pane = StackPane(control).pad(5.0)
            val popup = Popup().sceneFill(Color.BLACK)
            popup.content.add(pane)
            popup.scene.initHextantScene(ctx)

            popup.scene.registerShortcuts {
                on("Ctrl+Enter") {
                    val expr = editor.result.now
                    val code = expr.code(ctx)
                    ctx[SuperColliderClient].eval(code)
                        .handleAsync { result, exception ->
                            val result =
                                if (exception != null) exception.message ?: "Unknown Error"
                                else result
                            Platform.runLater {
                                ResultPopup(ctx, result, error = exception != null).show()
                                popup.hide()
                            }
                        }
                }
            }
            popup.show(layout.scene.window)
            runFXWithTimeout(50) {
                popup.requestFocus()
            }
        }
    }
})