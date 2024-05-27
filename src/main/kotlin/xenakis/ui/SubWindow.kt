package xenakis.ui

import hextant.context.Context
import hextant.fx.Stylesheets
import hextant.fx.registerShortcuts
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.layout.Pane
import javafx.scene.layout.Region
import javafx.stage.Modality
import javafx.stage.Stage
import javafx.stage.StageStyle
import xenakis.ui.XenakisApp.Companion.primaryStage

class SubWindow(
    private val root: Parent,
    title: String,
    private val context: Context,
    private val type: Type = Type.Modal,
    applyStylesheets: Boolean = true,
    private val parent: Pane? = null,
    private val onShowing: SubWindow.() -> Unit = {}
) : Stage() {
    private var idxInParent = -1

    init {
        initOwner(context[primaryStage])
        this.title = title
        scene = Scene(Pane())
        if (applyStylesheets) applyStylesheets()
        initWindowType()
        removeRootFromParentOnShowing()
        sizeToScene()
    }

    private fun applyStylesheets() {
        context[Stylesheets].manage(scene)
    }

    private fun initWindowType() {
        if (type in setOf(Type.Prompt, Type.Popup)) {
            scene.registerShortcuts {
                on("ESCAPE") { hide() }
            }
            focusedProperty().addListener { _, _, hasFocus ->
                if (!hasFocus) hide()
            }
            initStyle(StageStyle.UNDECORATED)
            initModality(Modality.NONE)
        } else {
            initStyle(StageStyle.DECORATED)
            initModality(Modality.WINDOW_MODAL)
        }
    }

    private fun removeRootFromParentOnShowing() {
        setOnShowing {
            onShowing()
            if (parent != null) {
                idxInParent = parent.children.indexOf(root)
                parent.children.removeAt(idxInParent)
            }
            scene.root = root
            root.requestFocus()
            sizeToScene()
        }
    }

    override fun hide() {
        scene.root = Region()
        parent?.children?.add(idxInParent, root)
        super.hide()
    }

    enum class Type {
        Popup, Prompt, Modal
    }
}