package xenakis.ui.impl

import javafx.application.Platform
import org.controlsfx.control.Notifications
import xenakis.model.Logger
import xenakis.model.Logger.Level.*
import java.io.PrintWriter
import java.io.StringWriter

object NotificationView : Logger.View {
    override fun logged(record: Logger.Record) {
        if (record.level < level) return
        val notification = Notifications.create().text(record.message).darkStyle()
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
    }

    override fun clearedLog() {
    }

    var level = Confirmation
}

fun <T : Any> tryWithAlert(actionDescription: String, category: Logger.Category? = null, action: () -> T): T? = try {
    action()
} catch (e: Exception) {
    val stackTrace = e.stackTraceString()
    Logger.error("Exception while $actionDescription: ${e.message}", category, detailMessage = stackTrace)
    null
}

fun Throwable.stackTraceString(): String {
    val writer = StringWriter()
    printStackTrace(PrintWriter(writer))
    val stackTrace = writer.toString()
    return stackTrace
}