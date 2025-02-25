package xenakis.ui.launcher

import hextant.context.Context
import javafx.application.Platform
import javafx.scene.control.Label
import javafx.scene.control.ProgressBar
import javafx.scene.image.ImageView
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.stage.StageStyle

class LoadingScreen(override val context: Context): Activity(), ProgressIndicator {
    private val progressBar = ProgressBar()
    private val statusText = Label()
    private val logo = ImageView(APP_ICON)

    init {
        logo.isPreserveRatio = true
        logo.fitWidth = 500.0
        progressBar.prefWidth = logo.prefWidth(-1.0)
    }

    override fun getLayout() = VBox(logo, StackPane(progressBar, statusText))

    override fun beforeShowing() {
        stage.sizeToScene()
        stage.titleProperty().bind(statusText.textProperty())
        stage.initStyle(StageStyle.UNDECORATED)
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