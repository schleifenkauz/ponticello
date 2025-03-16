package xenakis.ui.actions

import fxutils.actions.Action
import fxutils.actions.isShiftDown
import javafx.scene.layout.Region
import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.materialdesign2.MaterialDesignM
import reaktive.value.binding.map
import reaktive.value.now
import xenakis.model.player.PlaybackManager
import xenakis.model.registry.BusRegistry
import xenakis.model.registry.reference
import xenakis.sc.Rate
import xenakis.ui.launcher.XenakisLauncher.Companion.currentProject
import xenakis.ui.registry.SearchableBusListView

object PlaybackActions : Action.Collector<PlaybackManager>({
    addAction("Go to start") {
        description("Move the playback cursor to the start of the score")
        shortcut("Ctrl+DIGIT0")
        icon(Material2MZ.SKIP_PREVIOUS)
        executes { playback -> playback.movePlayHeadToStart() }
    }
    addAction("Toggle Playback") {
        shortcut("Ctrl+SPACE")
        icon { playback ->
            playback.player.isPlaying.map { playing ->
                if (playing) Material2MZ.PAUSE
                else Material2MZ.PLAY_ARROW
            }
        }
        executes { playback ->
            if (!playback.player.isPlaying.now) {
                playback.player.play()
            } else {
                playback.player.pause()
            }
        }
    }
    addAction("Stop") {
        description("Stop playback and free all Synths")
        shortcut("Ctrl+PERIOD")
        icon(Material2MZ.STOP)
        executes { playback -> playback.player.reset() }
    }
    addAction("Toggle recording") {
        shortcut("Ctrl+R")
        icon { playback ->
            playback.recorder.isActive.map { active ->
                if (active) MaterialDesignM.MICROPHONE
                else MaterialDesignM.MICROPHONE_OUTLINE
            }
        }
        executes { playback, ev ->
            if (ev.isShiftDown()) {
                val context = playback.context
                val project = context[currentProject]
                val currentSelected =
                    project.serverOptions.recordedBus.get() ?: context[BusRegistry].getDefault()
                SearchableBusListView(context[BusRegistry], "Select bus to record to", rate = Rate.Audio).showPopup(
                    anchorNode = ev?.source as Region,
                    initialOption = currentSelected
                ) { bus -> project.serverOptions.recordedBus = bus.reference() }
            } else playback.recorder.toggleIsActive()
        }
    }
})