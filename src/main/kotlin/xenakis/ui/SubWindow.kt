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
import javafx.stage.Window
import xenakis.ui.XenakisApp.Companion.primaryStage

class SubWindow(
    private val root: Parent,
    title: String,
    context: Context,
    private val type: Type = Type.Modal,
    owner: Window? = context[primaryStage],
    applyStylesheets: Boolean = true
) : Stage() {
    init {
        initOwner(owner)
        this.title = title
        scene = Scene(root)
        if (applyStylesheets) context[Stylesheets].manage(scene)
        initWindowType()
        setOnShowing {
            root.requestFocus()
        }
    }

    private fun initWindowType() {
        val style = when (type) {
            Type.Modal -> StageStyle.DECORATED
            Type.Popup -> StageStyle.TRANSPARENT
            Type.Undecorated -> StageStyle.TRANSPARENT
        }
        initStyle(style)
        if (type == Type.Popup) {
            scene.registerShortcuts {
                on("ESCAPE") { hide() }
            }
            focusedProperty().addListener { _, _, hasFocus ->
                if (!hasFocus) hide()
            }
            initModality(Modality.NONE)
        } else {
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
        Popup, Undecorated, Modal;
    }
}