package ponticello.ui.actions

import fxutils.actions.*
import fxutils.sourceWindow
import javafx.event.Event
import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.materialdesign2.MaterialDesignD
import org.kordamp.ikonli.materialdesign2.MaterialDesignM
import ponticello.model.flow.AudioFlows
import ponticello.model.flow.NodeTree
import ponticello.model.live.LiveObjectRegistry
import ponticello.model.obj.project
import ponticello.model.player.ActiveObjectsManager
import ponticello.model.player.Recorder
import ponticello.model.player.ScorePlayer
import ponticello.model.project.PLAYBACK_SETTINGS
import ponticello.model.project.SERVER_OPTIONS
import ponticello.model.project.get
import ponticello.model.registry.reference
import ponticello.model.server.BusRegistry
import ponticello.sc.Rate
import ponticello.sc.client.SuperColliderClient
import ponticello.ui.misc.PlayHead
import ponticello.ui.misc.PlaybackSettingsPrompt
import ponticello.ui.registry.BusSelectorPrompt
import reaktive.value.binding.and
import reaktive.value.binding.map
import reaktive.value.binding.not
import reaktive.value.now

object PlaybackActions {
    fun goToStartAction(shortcut: String) = action<PlayHead>("Go to start") {
        description("Move the playback cursor to the start of the score")
        shortcut(shortcut)
        icon(Material2MZ.SKIP_PREVIOUS)
        enableWhen { playHead -> playHead.canMoveManually }
        executes { playHead, ev ->
            if (ev.isTargetTextInput && !ev.isAltDown() && !ev.isControlDown()) return@executes
            if (playHead.canMoveManually.now) {
                playHead.movePlayHeadToStart()
            }
        }
    }

    fun playAction(shortcut: String) = action<ScorePlayer>("Toggle Playback") {
        shortcut(shortcut)
        icon { player ->
            player.isPlaying.map { playing ->
                if (playing) Material2MZ.PAUSE
                else Material2MZ.PLAY_ARROW
            }
        }
        toggleState { player -> player.isScheduled and player.isPlaying.not() }
        executes { player, ev ->
            if (ev.isTargetTextInput && !ev.isAltDown() && !ev.isControlDown()) return@executes
            if (!player.isScheduled.now) {
                player.play()
            } else {
                player.pause()
            }
        }
    }

    val toggleRecording: Action<ScorePlayer> = action("Toggle Recording") {
//        shortcut("Ctrl+Alt+Shift?+R")
        icon { player ->
            player.context[Recorder].isRecording.map { recording ->
                if (recording) MaterialDesignM.MICROPHONE
                else MaterialDesignM.MICROPHONE_OUTLINE
            }
        }
        toggleState { player ->
            val recorder = player.context[Recorder]
            recorder.isActive and recorder.isRecording.not()
        }
        executes { player, ev ->
            if (ev.isShiftDown()) {
                selectRecordedBus(player, ev)
            } else player.context[Recorder].toggle()
        }
    }

    fun selectRecordedBus(player: ScorePlayer, ev: Event?) {
        val context = player.context
        val project = context.project
        val currentSelected =
            project[SERVER_OPTIONS].recordedBus.get() ?: context[BusRegistry].getDefault()
        val bus = BusSelectorPrompt(
            context[BusRegistry],
            "Select bus to record to", rate = Rate.Audio
        ).selectInitialOption(currentSelected).showDialog(ev)
        if (bus != null) project[SERVER_OPTIONS].recordedBus = bus.reference()
    }

    val global = collectActions<ScorePlayer> {
        add(goToStartAction("Alt?+DIGIT0")) { player -> player.playHead }
        add(playAction("Alt?+SPACE"))
        addAction("Stop") {
            description("Stop playback and free all Synths")
            shortcut("Ctrl+PERIOD")
            icon(Material2MZ.STOP)
            executes { p ->
                p.context[AudioFlows].writeVSTPluginStates()
                p.context[Recorder].stopRecording()
                for (liveObject in p.context[LiveObjectRegistry]) {
                    liveObject.pause()
                }
                p.context[ActiveObjectsManager].clear()
                p.context[NodeTree].clear()
                p.context[SuperColliderClient].run("s.freeAll")
                p.pause()
            }
        }
        addAction("Recompute score object intervals") {
            shortcut("Shift+F5")
            executes { p ->
                p.score.recomputeIntervals()
            }
        }
        addAction("Open playback settings") {
            shortcut("Ctrl+Shift+P")
            icon(MaterialDesignD.DOTS_VERTICAL) //COG_PLAY_OUTLINE is given in the cheat sheet
            executes { player, ev ->
                val settings = player.context.project[PLAYBACK_SETTINGS]
                val pane = PlaybackSettingsPrompt(settings)
                pane.showDialog(ev.sourceWindow)
            }
        }
    }
}