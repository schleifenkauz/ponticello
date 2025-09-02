package ponticello.ui.misc

import fxutils.styleClass
import hextant.context.Context
import javafx.application.Platform
import javafx.scene.control.Label
import javafx.scene.control.Tooltip
import ponticello.impl.Decimal
import ponticello.ui.launcher.PonticelloLauncher
import ponticello.ui.launcher.PonticelloMainActivity
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TimeWarpPopup(private val context: Context) : Tooltip() {
    private val label = Label() styleClass "time-warp-label"
    private var updateTimestamp = 0L

    init {
        graphic = label
        centerOnScreen()
    }

    fun update(timeWarp: Decimal) = Platform.runLater {
        label.text = "Tempo multiplier: $timeWarp"
        val timestamp = System.currentTimeMillis()
        updateTimestamp = timestamp
        if (!isShowing) {
            val mainActivity = context[PonticelloLauncher].currentActivity as? PonticelloMainActivity ?: return@runLater
            val primaryStage = mainActivity.primaryStage
            show(primaryStage, primaryStage.x + primaryStage.width / 5, primaryStage.y)
        }
        executor.schedule(Hider(timestamp), HIDE_DELAY, TimeUnit.MILLISECONDS)
    }

    private inner class Hider(private val timestamp: Long) : Runnable {
        override fun run() {
            if (timestamp == updateTimestamp) {
                Platform.runLater {
                    hide()
                }
            }
        }
    }

    companion object {
        private val executor = Executors.newSingleThreadScheduledExecutor()

        private const val HIDE_DELAY = 2000L
    }
}