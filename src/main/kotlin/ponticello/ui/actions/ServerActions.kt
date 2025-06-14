package ponticello.ui.actions

import fxutils.actions.Action
import fxutils.actions.action
import fxutils.actions.isShiftDown
import org.kordamp.ikonli.evaicons.Evaicons
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import ponticello.impl.Logger
import ponticello.model.obj.BusReference
import ponticello.model.project.PonticelloProject
import ponticello.model.project.SERVER_OPTIONS
import ponticello.model.project.get
import ponticello.sc.client.SuperColliderClient
import ponticello.ui.impl.showDialog
import ponticello.ui.misc.ServerOptionsPane
import reaktive.value.ReactiveValue
import reaktive.value.binding.flatMap
import reaktive.value.now

object ServerActions : Action.Collector<PonticelloProject>({
    addAction("Reboot server") {
        shortcut("Ctrl+Shift?+B")
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
        executes { project -> project.client.run("AppClock.sched(0) { s.plotTree }") }
    }
    addAction("Monitor output") {
        shortcut("Ctrl+Shift+M")
        executes { project -> project.client.run("AppClock.sched(0) { s.scope }") }
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
            bus.context[SuperColliderClient].run("AppClock.sched(0) { ${bus.superColliderName}.scope; }")
        }
    }
}