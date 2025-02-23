package xenakis.ui.launcher

import bundles.publicProperty
import com.pixelduke.window.ThemeWindowManagerFactory
import javafx.application.Application
import javafx.stage.Stage
import xenakis.model.Logger
import xenakis.ui.impl.NotificationView
import xenakis.ui.impl.stackTraceString

class XenakisApp : Application() {
    private lateinit var launcher: XenakisLauncher

    override fun start(stage: Stage) {
        setupLogging()
        ThemeWindowManagerFactory.create().setDarkModeForWindowFrame(stage, true)
        launcher = XenakisLauncher()
        launcher.launchXenakis(stage)
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
        launcher.quitApplication()
    }

    companion object {
        val primaryStage = publicProperty<Stage>("primary-stage")

        @JvmStatic
        fun main(args: Array<String>) {
            launch(XenakisApp::class.java)
        }
    }
}