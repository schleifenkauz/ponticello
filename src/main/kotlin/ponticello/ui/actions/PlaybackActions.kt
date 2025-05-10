package ponticello.ui.actions

import fxutils.actions.*
import javafx.event.Event
import javafx.scene.input.DataFormat
import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.materialdesign2.MaterialDesignM
import reaktive.value.binding.and
import reaktive.value.binding.map
import reaktive.value.binding.not
import reaktive.value.now
import ponticello.model.flow.NodeTree
import ponticello.model.player.ActiveObjectsManager
import ponticello.model.player.Recorder
import ponticello.model.player.ScorePlayer
import ponticello.model.project.SERVER_OPTIONS
import ponticello.model.project.get
import ponticello.model.registry.BusRegistry
import ponticello.model.registry.reference
import ponticello.sc.Rate
import ponticello.sc.client.SuperColliderClient
import ponticello.ui.launcher.PonticelloLauncher.Companion.currentProject
import ponticello.ui.registry.SearchableBusListView

object PlaybackActions {
    private fun goToStartAction(shortcut: String) = action<ScorePlayer>("Go to start") {
        description("Move the playback cursor to the start of the score")
        shortcut(shortcut)
        icon(Material2MZ.SKIP_PREVIOUS)
        enableWhen { player -> player.isScheduled.not() }
        executes { player, ev ->
            if (ev.isTargetTextInput && !ev.isAltDown() && !ev.isControlDown()) return@executes
            if (!player.isScheduled.now) {
                player.playHead.movePlayHeadToStart()
            }
        }
    }

    private fun playAction(shortcut: String) = action<ScorePlayer>("Toggle Playback") {
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

    val local = collectActions {
        add(goToStartAction("Ctrl+DIGIT0"))
        add(playAction("Ctrl+SPACE"))
    }

    val toggleRecording: Action<ScorePlayer> = action("Toggle Recording") {
        shortcut("Ctrl+Alt+Shift?+R")
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

    val RECORD_BUTTON = DataFormat("record-button")

    fun selectRecordedBus(player: ScorePlayer, ev: Event?) {
        val context = player.context
        val project = context[currentProject]
        val currentSelected =
            project[SERVER_OPTIONS].recordedBus.get() ?: context[BusRegistry].getDefault()
        val bus = SearchableBusListView(
            context[BusRegistry],
            "Select bus to record to", rate = Rate.Audio
        ).showPopup(ev, initialOption = currentSelected)
        if (bus != null) project[SERVER_OPTIONS].recordedBus = bus.reference()
    }

    val global = collectActions<ScorePlayer> {
        add(goToStartAction("Alt?+DIGIT0"))
        add(playAction("Alt?+SPACE"))
        addAction("Stop") {
            description("Stop playback and free all Synths")
            shortcut("Ctrl+PERIOD")
            icon(Material2MZ.STOP)
            executes { p ->
                p.context[Recorder].stopRecording()
                for (player in ScorePlayer.instances()) {
                    player.pause()
                }
                p.context[ActiveObjectsManager].clear()
                p.context[NodeTree].clear()
                p.context[SuperColliderClient].run("s.freeAll")
            }
        }
        add(toggleRecording)
    }
}