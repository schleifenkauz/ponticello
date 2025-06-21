package ponticello.ui.launcher

import bundles.publicProperty
import javafx.application.Application
import javafx.stage.Stage
import ponticello.impl.Logger
import ponticello.model.GlobalSettings
import ponticello.ui.impl.NotificationView
import reaktive.value.now
import kotlin.concurrent.thread

class PonticelloApp : Application() {
    private lateinit var launcher: PonticelloLauncher

    override fun start(stage: Stage) {
        val projectPath = parameters.raw.getOrNull(0)
        setupLogging()
        launcher = PonticelloLauncher()
        launcher.launchPonticello(stage, projectPath)
        periodicGC()
    }

    private fun periodicGC() {
        thread(isDaemon = true, name = "Periodic GC") {
            while (true) {
                val settings = launcher.rootContext[GlobalSettings]
                val period = settings.garbageCollectionPeriod.now.toLong()
                Thread.sleep(period * 1000)
                if (settings.periodicGarbageCollection.now) {
                    Logger.info("Performing periodic garbage collection...", Logger.Category.Memory)
                    System.gc()
                }
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
            launch(PonticelloApp::class.java, *args)
        }
    }
}