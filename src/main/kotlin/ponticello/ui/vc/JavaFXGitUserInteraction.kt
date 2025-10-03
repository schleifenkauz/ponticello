package ponticello.ui.vc

import fxutils.SubWindow
import fxutils.actions.button
import fxutils.centerChildren
import fxutils.prompt.YesNoPrompt
import javafx.application.Platform
import javafx.scene.control.Label
import javafx.scene.control.ProgressBar
import javafx.scene.input.Clipboard
import javafx.scene.input.DataFormat
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import ponticello.model.git.GitUserInteraction

object JavaFXGitUserInteraction : GitUserInteraction, HBox(5.0) {
    private val progressBar = ProgressBar()
    private val taskLabel = Label()
    private val statusLabel = Label()

    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    private var cancel = false
    private val cancelButton = MaterialDesignC.CLOSE.button("Cancel", "small-icon-button") {
        cancel = true
        currentJob?.cancel()
        currentJob = null
    }

    private val window by lazy {
        SubWindow(this, "Git action status", SubWindow.Type.Undecorated)
    }

    private var currentJob: Job? = null
    private var totalTasks = 0
    private var finishedTasks = 0
    private var currentTaskUnits = 0

    init {
        Platform.runLater {
            children.addAll(
                VBox(
                    progressBar,
                    HBox(5.0, statusLabel, taskLabel)
                ),
                cancelButton
            )
            centerChildren()

            progressBar.prefWidth = 200.0
        }
    }

    override fun launch(block: suspend () -> Unit) {
        currentJob = scope.launch {
            block()
            currentJob = null
        }
    }

    override fun authenticate(userCode: String, verificationUri: String) = Platform.runLater {
        val doIt = YesNoPrompt(
            "Your code is $userCode.\nCopy to clipboard and open the verification page?",
            default = true
        ).showDialog()
        if (doIt == true) {
            Clipboard.getSystemClipboard().setContent(mapOf(DataFormat.PLAIN_TEXT to userCode))
            println(verificationUri)
//            Desktop.getDesktop().browse(URI(verificationUri))
        } else {
            currentJob?.cancel()
        }
    }

    override fun start(totalTasks: Int) = Platform.runLater {
        this.totalTasks = totalTasks
        finishedTasks = 0
        statusLabel.text = "$finishedTasks/$totalTasks"
        window.show()
    }

    override fun beginTask(title: String?, totalWork: Int) = Platform.runLater {
        taskLabel.text = title ?: "<?>"
        currentTaskUnits = totalWork
        progressBar.progress = 0.0
    }

    override fun update(completed: Int) = Platform.runLater {
        progressBar.progress = currentTaskUnits.toDouble() / completed
    }

    override fun endTask() = Platform.runLater {
        finishedTasks += 1
        statusLabel.text = "$finishedTasks/$totalTasks"
        if (finishedTasks == totalTasks) {
            window.hide()
        }
    }

    override fun isCancelled(): Boolean = cancel

    override fun showDuration(enabled: Boolean) {
    }
}