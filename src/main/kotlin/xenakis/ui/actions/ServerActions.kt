package xenakis.ui.actions

import fxutils.actions.Action
import fxutils.actions.isShiftDown
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import xenakis.model.XenakisProject
import xenakis.ui.impl.showDialog
import xenakis.ui.misc.ServerOptionsPane

object ServerActions : Action.Collector<XenakisProject>({
    addAction("Reboot server") {
        shortcut("Shift?+F5")
        icon(MaterialDesignR.RESTART)
        executes { project, ev ->
            if (ev.isShiftDown()) {
                ServerOptionsPane(project.context, project.serverOptions).showDialog(project.context)
            } else {
                project.rebootServer()
            }
        }
    }
    addAction("Sync with SuperCollider") {
        shortcut("Ctrl+Shift+S")
        executes { project -> project.syncWithSuperCollider() }
    }
    addAction("Plot Server Tree") {
        shortcut("Ctrl+Alt+T")
        executes { project -> project.client.run("s.plotTree") }
    }
    addAction("Monitor output") {
        shortcut("Ctrl+Shift+M")
        executes { project -> project.client.run("s.scope") }
    }
    addAction("Show ServerMeter") {
        shortcut("Ctrl+M")
        executes { project ->
            val numIns = project.serverOptions.numInputChannels
            val numOuts = project.serverOptions.numOutputChannels
            project.client.run("ServerMeter.new(s, $numIns, $numOuts)")
        }
    }
})