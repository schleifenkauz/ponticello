package ponticello.ui.impl

import javafx.application.Platform
import javafx.util.Duration
import org.controlsfx.control.Notifications
import ponticello.impl.Logger
import ponticello.impl.Logger.Level.*
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object NotificationView : Logger.View {
    var level = Confirmation

    var maximumSimultaneousNotifications = 5

    private var showingNotifications = 0

    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "NotificationsCounter").apply { isDaemon = true }
    }

    override fun logged(record: Logger.Record) {
        if (record.level < level) return
        val millis = 5000L
        val notification = Notifications.create()
            .text(record.message).darkStyle()
            .hideAfter(Duration.millis(millis.toDouble()))
        if (showingNotifications + 1 > maximumSimultaneousNotifications) return
        Platform.runLater {
            try {
                when (record.level) {
                    Fine, Info -> notification.showInformation()
                    Confirmation -> notification.showConfirm()
                    Warning -> notification.showWarning()
                    Error -> notification.showError()
                }
            } catch (e: NullPointerException) {
                return@runLater //main window already closed
            }
        }
        showingNotifications++
        executor.schedule({ showingNotifications-- }, millis, TimeUnit.MILLISECONDS)
    }

    override fun clearedLog() {
    }
}

fun <T : Any> tryWithAlert(actionDescription: String, category: Logger.Category? = null, action: () -> T): T? = try {
    action()
} catch (e: Exception) {
    Logger.error("Exception while $actionDescription: ${e.message}", e, category)
    null
}

fun Throwable.stackTraceString(): String {
    val writer = StringWriter()
    printStackTrace(PrintWriter(writer))
    val stackTrace = writer.toString()
    return stackTrace
}