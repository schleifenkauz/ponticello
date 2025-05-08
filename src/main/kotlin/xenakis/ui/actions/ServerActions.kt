package xenakis.ui.actions

import fxutils.actions.Action
import fxutils.actions.action
import fxutils.actions.isShiftDown
import org.kordamp.ikonli.evaicons.Evaicons
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import reaktive.value.ReactiveValue
import reaktive.value.binding.flatMap
import reaktive.value.now
import xenakis.impl.Logger
import xenakis.model.obj.BusReference
import xenakis.model.project.SERVER_OPTIONS
import xenakis.model.project.XenakisProject
import xenakis.model.project.get
import xenakis.sc.client.SuperColliderClient
import xenakis.ui.impl.showDialog
import xenakis.ui.misc.ServerOptionsPane

object ServerActions : Action.Collector<XenakisProject>({
    addAction("Reboot server") {
        shortcut("Shift?+F5")
        icon(MaterialDesignR.RESTART)
        executes { project, ev ->
            if (ev.isShiftDown()) {
                ServerOptionsPane(project.context, project[SERVER_OPTIONS]).showDialog(project.context)
            } else {
                project.rebootServer()
            }
        }
    }
    addAction("Sync with SuperCollider") {
        shortcut("Ctrl+F5")
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
        shortcut("Ctrl+Shift+M")
        executes { project ->
            val numIns = project[SERVER_OPTIONS].numInputChannels
            val numOuts = project[SERVER_OPTIONS].numOutputChannels
            project.client.run("ServerMeter.new(s, $numIns, $numOuts)")
        }
    }
}) {
    val scopeBus = action<ReactiveValue<BusReference>>("Scope") {
        icon(Evaicons.ACTIVITY)
        enableWhen { ref -> ref.flatMap(BusReference::isResolved) }
        executes { ref ->
            val bus = ref.now.get()
            if (bus == null) {
                Logger.warn("Bus $ref is not resolved", Logger.Category.Registries)
                return@executes
            }
            bus.context[SuperColliderClient].run("${bus.superColliderName}.scope;")
        }
    }
}