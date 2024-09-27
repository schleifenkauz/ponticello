package xenakis.ui

import hextant.context.Context
import hextant.fx.Stylesheets
import hextant.fx.registerShortcuts
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.layout.Region
import javafx.stage.Modality
import javafx.stage.Stage
import javafx.stage.StageStyle
import xenakis.ui.XenakisApp.Companion.primaryStage

class SubWindow(
    private val root: Parent,
    title: String,
    context: Context,
    private val type: Type = Type.Modal,
    applyStylesheets: Boolean = true,
    private val onShowing: SubWindow.() -> Unit = {}
) : Stage() {
    init {
        initOwner(context[primaryStage])
        this.title = title
        scene = Scene(root)
        if (applyStylesheets) context[Stylesheets].manage(scene)
        initWindowType()
        setOnShowing {
            root.requestFocus()
            onShowing()
        }
    }

    private fun initWindowType() {
        if (type in setOf(Type.Prompt, Type.Popup)) {
            scene.registerShortcuts {
                on("ESCAPE") { hide() }
            }
            if (type == Type.Popup) {
                focusedProperty().addListener { _, _, hasFocus ->
                    if (!hasFocus) hide()
                }
            }
            initStyle(StageStyle.UNDECORATED)
            initModality(Modality.NONE)
        } else {
            initStyle(StageStyle.DECORATED)
            initModality(Modality.WINDOW_MODAL)
            scene.registerShortcuts {
                on("Ctrl+W") { hide() }
            }
        }
    }

    @Suppress("unused")
    fun autoResize() {
        require(root is Region)
        root.widthProperty().addListener { _, _, _ -> sizeToScene() }
        root.widthProperty().addListener { _, _, _ -> sizeToScene() }
        isResizable = false
    }

    enum class Type {
        Popup, Prompt, Modal
    }
}