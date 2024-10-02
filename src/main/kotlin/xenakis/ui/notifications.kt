package xenakis.ui

import javafx.application.Platform
import javafx.scene.control.Alert
import org.controlsfx.control.Notifications
import xenakis.model.Logger
import xenakis.model.Logger.Level.*
import java.io.PrintWriter
import java.io.StringWriter

fun alertException(action: String, exc: Exception) = Alert(Alert.AlertType.ERROR).run {
    headerText = "Exception while: $action"
    contentText = exc.message
    show()
}

fun alertError(text: String) = Platform.runLater { Notifications.create().text(text).darkStyle().showError() }

object NotificationView : Logger.View {
    override fun logged(record: Logger.Record) {
        if (record.level < level) return
        val notification = Notifications.create().text(record.message).darkStyle()
        Platform.runLater {
            when (record.level) {
                Fine, Info -> notification.showInformation()
                Confirmation -> notification.showConfirm()
                Warning -> notification.showWarning()
                Error -> notification.showError()
            }
        }
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