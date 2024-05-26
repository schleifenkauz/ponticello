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
    applyStylesheets: Boolean = true,
    autoHide: Boolean = true,
    style: StageStyle = StageStyle.DECORATED,
    private val parent: Pane? = null,
    private val onShowing: () -> Unit = {}
) : Stage() {
    private var idxInParent = -1

    init {
        initStyle(style)
        initModality(Modality.WINDOW_MODAL)
        initOwner(context[primaryStage])
        this.title = title
        scene = Scene(Pane())
        if (applyStylesheets) applyStylesheets()
        if (autoHide) autoHide()
        removeRootFromParentOnShowing()
        sizeToScene()
    }

    private fun applyStylesheets() {
        context[Stylesheets].manage(scene)
    }

    private fun autoHide() {
        scene.registerShortcuts {
            on("ESCAPE") { hide() }
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
            sizeToScene()
        }
    }

    override fun hide() {
        scene.root = Region()
        parent?.children?.add(idxInParent, root)
        super.hide()
    }
}