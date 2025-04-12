package xenakis.ui.launcher

import bundles.publicProperty
import javafx.application.Application
import javafx.stage.Stage
import reaktive.value.now
import xenakis.impl.Logger
import xenakis.model.Settings
import xenakis.ui.impl.NotificationView
import kotlin.concurrent.thread

class XenakisApp : Application() {
    private lateinit var launcher: XenakisLauncher

    override fun start(stage: Stage) {
        setupLogging()
        launcher = XenakisLauncher()
        launcher.launchXenakis(stage)
        periodicGC()
    }

    private fun periodicGC() {
        thread(isDaemon = true) {
            while (true) {
                System.gc()
                val period = launcher.rootContext[Settings].garbageCollectionPeriod.now.toLong()
                Thread.sleep(period * 1000)
            }
        }
    }

    private fun setupLogging() {
        Thread.currentThread().setUncaughtExceptionHandler { _, e ->
            e.printStackTrace()
            Logger.error(e.message ?: "<no message>", e)
        }
        Logger.level = Logger.Level.Fine
        NotificationView.level = Logger.Level.Confirmation
        Logger.addView(NotificationView)
    }

    companion object {
        val primaryStage = publicProperty<Stage>("primary-stage")

        @JvmStatic
        fun main(args: Array<String>) {
            launch(XenakisApp::class.java)
        }
    }
}