package xenakis.ui

import bundles.publicProperty
import javafx.application.Application
import javafx.stage.Stage

class XenakisApp : Application() {
    private lateinit var controller: XenakisController

    override fun start(stage: Stage) {
        controller = XenakisController(stage)
        controller.setupHextant()
        controller.startSuperCollider()
        val ui = XenakisUI(stage, controller)
        controller.addListener(ui)
        controller.startXenakis()
        stage.title = "Xenakis"
        stage.show()
    }

    override fun stop() {
        controller.quitApplication()
    }

    companion object {
        val primaryStage = publicProperty<Stage>("primary-stage")

        @JvmStatic
        fun main(args: Array<String>) {
            launch(XenakisApp::class.java)
        }
    }
}