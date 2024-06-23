package xenakis.ui

import bundles.publicProperty
import com.pixelduke.window.ThemeWindowManagerFactory
import javafx.application.Application
import javafx.stage.Stage
import xenakis.impl.isMyComputerDumb
import xenakis.ui.XenakisUI.Mode
import java.util.logging.Level
import java.util.logging.Logger
import java.util.logging.SimpleFormatter
import java.util.logging.StreamHandler
import kotlin.concurrent.thread

class XenakisApp : Application() {
    private lateinit var controller: XenakisController

    override fun start(stage: Stage) {
        setupLogging()
        controller = XenakisController(stage)
        controller.setupHextant()
        val ui = setupStage(stage)
        stage.show()
        ThemeWindowManagerFactory.create().setDarkModeForWindowFrame(stage, true)

        controller.addListener(ui)
        ui.displayLoadScreen()
        thread {
            controller.startSuperCollider()
        }
    }

    private fun setupStage(stage: Stage): XenakisUI {
        val mode = if (isMyComputerDumb) Mode.Laptop else Mode.Desktop
        val ui = XenakisUI(stage, controller, mode)
        stage.setOnCloseRequest {
            if (controller.isProjectOpened) {
                val save = showYesNoDialog("Save project?", default = true) ?: return@setOnCloseRequest
                if (save) controller.saveProject()
                stage.hide()
            }
        }
        stage.icons.setAll(Icon.AppIcon.image)
        stage.title = "Xenakis"
        return ui
    }

    private fun setupLogging() {
        val handler = StreamHandler(System.out, SimpleFormatter())
        handler.level = Level.ALL
        Logger.getGlobal().addHandler(handler)
        Logger.getGlobal().level = Level.FINE
    }

    override fun stop() {
        controller.quitApplication()
    }

    companion object {
        val primaryStage = publicProperty<Stage>("primary-stage")

        @JvmStatic
        fun main(args: Array<String>) {
            launch(XenakisApp::class.java)
        }
    }
}