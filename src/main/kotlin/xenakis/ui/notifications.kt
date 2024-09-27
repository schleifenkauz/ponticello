package xenakis.ui

import javafx.application.Platform
import javafx.scene.control.Alert
import org.controlsfx.control.Notifications

fun alertException(action: String, exc: Exception) = Alert(Alert.AlertType.ERROR).run {
    headerText = "Exception while: $action"
    contentText = exc.message
    show()
}

fun alertError(text: String) = Platform.runLater { Notifications.create().text(text).darkStyle().showError() }

fun notifyConfirm(message: String) =
    Platform.runLater { Notifications.create().text(message).darkStyle().showConfirm() }

fun notifyInfo(message: String) =
    Platform.runLater { Notifications.create().text(message).darkStyle().showInformation() }

fun <T : Any> tryWithAlert(actionDescription: String, action: () -> T): T? = try {
    action()
} catch (e: Exception) {
    e.printStackTrace()
    alertError("Exception while $actionDescription: ${e.message}")
    null
}