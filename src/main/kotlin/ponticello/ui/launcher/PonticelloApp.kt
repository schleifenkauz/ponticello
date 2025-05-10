package ponticello.ui.launcher

import bundles.publicProperty
import javafx.application.Application
import javafx.stage.Stage
import ponticello.impl.Logger
import ponticello.model.Settings
import ponticello.ui.impl.NotificationView
import reaktive.value.now
import kotlin.concurrent.thread

class PonticelloApp : Application() {
    private lateinit var launcher: PonticelloLauncher

    override fun start(stage: Stage) {
        setupLogging()
        launcher = PonticelloLauncher()
        launcher.launchPonticello(stage)
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
            launch(PonticelloApp::class.java)
        }
    }
}