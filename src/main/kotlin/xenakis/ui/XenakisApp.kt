package xenakis.ui

import javafx.application.Application
import javafx.stage.Stage

class XenakisApp : Application() {
    override fun start(stage: Stage) {
        val controller = XenakisController(stage)
        controller.setupHextant()
        controller.startSuperCollider()
        val ui = XenakisUI(stage, controller)
        controller.addListener(ui)
        controller.startXenakis()
        stage.show()
    }


    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            launch(XenakisApp::class.java)
        }
    }
}