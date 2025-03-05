package xenakis.ui.impl

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
import xenakis.ui.launcher.XenakisApp.Companion.primaryStage

class SubWindow(
    private val root: Parent,
    title: String,
    context: Context,
    private val type: Type = Type.ToolWindow,
    applyStylesheets: Boolean = true,
    customOwnerWindow: Window? = null,
) : Stage() {
    init {
        this.title = title
        scene = Scene(root)
        if (applyStylesheets) context[Stylesheets].manage(scene)
        initWindowType()
        registerShortcuts()
        initOwner(customOwnerWindow ?: context[primaryStage])
        setOnShowing {
            root.requestFocus()
        }
    }

    private fun initWindowType() {
        when (type) {
            Type.Popup -> {
                focusedProperty().addListener { _, _, hasFocus ->
                    if (!hasFocus) hide()
                }
                initStyle(StageStyle.TRANSPARENT)
            }

            Type.Prompt -> {
                initStyle(StageStyle.TRANSPARENT)
                initModality(Modality.WINDOW_MODAL)
            }

            Type.ToolWindow -> {
                initStyle(StageStyle.DECORATED)
            }

            Type.Undecorated -> {
                initStyle(StageStyle.TRANSPARENT)
            }
        }
    }

    private fun registerShortcuts() {
        if (type in setOf(Type.Popup, Type.Prompt, Type.Undecorated)) {
            scene.registerShortcuts {
                on("ESCAPE") { hide() }
            }
        } else {
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

    fun showOrBringToFront() {
        if (!isShowing) show()
        else toFront()
    }

    enum class Type {
        Popup, Undecorated, ToolWindow, Prompt;
    }
}