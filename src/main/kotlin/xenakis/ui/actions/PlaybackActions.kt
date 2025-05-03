package xenakis.ui.actions

import fxutils.actions.Action
import fxutils.actions.isAltDown
import fxutils.actions.isShiftDown
import fxutils.actions.isTargetTextInput
import javafx.scene.layout.Region
import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.materialdesign2.MaterialDesignM
import reaktive.value.binding.map
import reaktive.value.now
import xenakis.model.flow.NodeTree
import xenakis.model.player.Recorder
import xenakis.model.player.ScorePlayer
import xenakis.model.project.SERVER_OPTIONS
import xenakis.model.project.get
import xenakis.model.registry.BusRegistry
import xenakis.model.registry.reference
import xenakis.sc.Rate
import xenakis.sc.client.SuperColliderClient
import xenakis.ui.launcher.XenakisLauncher.Companion.currentProject
import xenakis.ui.registry.SearchableBusListView

object PlaybackActions : Action.Collector<ScorePlayer>({
    addAction("Go to start") {
        description("Move the playback cursor to the start of the score")
        shortcut("Alt?+DIGIT0")
        icon(Material2MZ.SKIP_PREVIOUS)
        executes { player, ev ->
            if (ev.isTargetTextInput && !ev.isAltDown()) return@executes
            player.movePlayHeadToStart()
        }
    }
    addAction("Toggle Playback") {
        shortcut("Alt?+SPACE")
        icon { player ->
            player.isPlaying.map { playing ->
                if (playing) Material2MZ.PAUSE
                else Material2MZ.PLAY_ARROW
            }
        }
        executes { player, ev ->
            if (ev.isTargetTextInput && !ev.isAltDown()) return@executes
            if (!player.isPlaying.now) {
                player.play()
            } else {
                player.pause()
            }
        }
    }
    addAction("Stop") {
        description("Stop playback and free all Synths")
        shortcut("Ctrl+PERIOD")
        icon(Material2MZ.STOP)
        executes { p ->
            p.context[Recorder].stopRecording()
            for (player in ScorePlayer.all()) {
                player.pause()
            }
            p.context[NodeTree].clear()
            p.context[SuperColliderClient].run("s.freeAll")
        }
    }
    addAction("Toggle recording") {
        shortcut("Ctrl+Shift+R")
        icon { player ->
            player.context[Recorder].isActive.map { active ->
                if (active) MaterialDesignM.MICROPHONE
                else MaterialDesignM.MICROPHONE_OUTLINE
            }
        }
        executes { player, ev ->
            if (ev.isShiftDown()) {
                val context = player.context
                val project = context[currentProject]
                val currentSelected =
                    project[SERVER_OPTIONS].recordedBus.get() ?: context[BusRegistry].getDefault()
                val bus = SearchableBusListView(
                    context[BusRegistry],
                    "Select bus to record to", rate = Rate.Audio
                ).showPopup(
                    anchorNode = ev?.source as Region,
                    initialOption = currentSelected
                )
                if (bus != null) project[SERVER_OPTIONS].recordedBus = bus.reference()
            } else player.context[Recorder].toggleIsActive()
        }
    }
})