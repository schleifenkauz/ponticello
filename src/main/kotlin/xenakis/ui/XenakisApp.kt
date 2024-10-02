package xenakis.ui

import bundles.publicProperty
import com.pixelduke.window.ThemeWindowManagerFactory
import javafx.application.Application
import javafx.stage.Screen
import javafx.stage.Stage
import xenakis.model.Logger
import xenakis.ui.XenakisUI.Mode
import kotlin.concurrent.thread

class XenakisApp : Application() {
    private lateinit var controller: XenakisController
    private lateinit var ui: XenakisUI

    override fun start(stage: Stage) {
        setupLogging()
        controller = XenakisController(stage)
        controller.setupHextant()
        ui = setupStage(stage)
        stage.show()
        ThemeWindowManagerFactory.create().setDarkModeForWindowFrame(stage, true)

        controller.addListener(ui)
        ui.displayLoadScreen()
        thread {
            controller.startSuperCollider()
        }
    }

    private fun setupStage(stage: Stage): XenakisUI {
        val largeScreenAvailable = Screen.getScreens().any { s -> s.bounds.width > 3000 }
        val mode = if (largeScreenAvailable) Mode.Desktop else Mode.Laptop
        val ui = XenakisUI(stage, controller, mode)
        stage.setOnCloseRequest { ev ->
            controller.closeRequest(stage)
            ev.consume()
        }
        stage.icons.setAll(Icon.AppIcon.image)
        stage.title = "Xenakis"
        return ui
    }

    private fun setupLogging() {
        Thread.currentThread().setUncaughtExceptionHandler { _, e ->
            e.printStackTrace()
            Logger.error(e.message ?: "<no message>", null, detailMessage = e.stackTraceString())
        }
        Logger.level = Logger.Level.Fine
        NotificationView.level = Logger.Level.Confirmation
        Logger.addView(NotificationView)
    }

    override fun stop() {
        ui.player.close()
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