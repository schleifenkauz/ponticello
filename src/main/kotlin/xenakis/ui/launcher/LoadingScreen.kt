package xenakis.ui.launcher

import hextant.context.Context
import javafx.application.Platform
import javafx.scene.control.Label
import javafx.scene.control.ProgressBar
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import xenakis.ui.Icon

class LoadingScreen(override val context: Context): Activity(), ProgressIndicator {
    private val progressBar = ProgressBar()
    private val statusText = Label()
    private val logo = Icon.AppIcon.getView(size = 500.0)

    init {
        progressBar.prefWidth = logo.prefWidth(-1.0)
    }

    override fun getLayout() = VBox(logo, StackPane(progressBar, statusText))

    override fun beforeShowing() {
        stage.sizeToScene()
        stage.show()
        stage.titleProperty().bind(statusText.textProperty())
    }

    override fun initialStatus(status: String) {
        progressBar.progress = 0.0
        statusText.text = status
    }

    override fun displayProgress(progress: Double, status: String) {
        Platform.runLater {
            progressBar.progress = progress
            statusText.text = status
        }
    }

    override fun increaseProgress(delta: Double, status: String) {
        displayProgress(progressBar.progress + delta, status)
    }
}