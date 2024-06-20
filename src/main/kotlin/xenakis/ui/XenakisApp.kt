package xenakis.ui

import bundles.publicProperty
import javafx.application.Application
import javafx.stage.Stage
import xenakis.impl.isMyComputerDumb
import xenakis.ui.XenakisUI.Mode
import java.util.logging.Level
import java.util.logging.Logger
import java.util.logging.SimpleFormatter
import java.util.logging.StreamHandler

class XenakisApp : Application() {
    private lateinit var controller: XenakisController

    override fun start(stage: Stage) {
        val handler = StreamHandler(System.out, SimpleFormatter())
        handler.level = Level.ALL
        Logger.getGlobal().addHandler(handler)
        Logger.getGlobal().level = Level.FINE
        controller = XenakisController(stage)
        controller.setupHextant()
        controller.startSuperCollider()
        val mode = if (isMyComputerDumb) Mode.Laptop else Mode.Desktop
        val ui = XenakisUI(stage, controller, mode)
        controller.addListener(ui)
        stage.setOnCloseRequest {
            if (controller.isProjectOpened) {
                val save = showYesNoDialog("Save project?", default = true) ?: return@setOnCloseRequest
                if (save) controller.saveProject()
                stage.hide()
            }
        }
        stage.title = "Xenakis"
        stage.show()
        controller.startXenakis()
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