package ponticello.ui.actions

import fxutils.actions.Action
import fxutils.actions.isAltDown
import fxutils.actions.isShiftDown
import fxutils.actions.isTargetTextInput
import fxutils.mouseX
import javafx.scene.robot.Robot
import org.kordamp.ikonli.material2.Material2MZ
import ponticello.impl.asTime
import ponticello.impl.one
import ponticello.impl.randomColor
import ponticello.impl.zero
import ponticello.model.flow.AudioFlowGroup
import ponticello.model.flow.AudioFlows
import ponticello.model.player.ScorePlayer
import ponticello.ui.controls.DecimalPrompt
import ponticello.ui.controls.NamePrompt
import ponticello.ui.score.NavigableScorePane
import reaktive.value.binding.not
import reaktive.value.now

object ScoreNavigationActions : Action.Collector<NavigableScorePane>({
    addAction("Move View To Start") {
        description("Moves the displayed portion of the score to the start")
        shortcut("HOME")
        executes { view -> view.display(0.0.asTime, view.displayedDuration) }
    }
    addAction("Display Whole Score") {
        shortcut("Ctrl+HOME")
        executes { view -> view.displayWholeScore() }
    }
    addAction("Move to Cursor") {
        description("Move displayed portion of the score to playback cursor")
        shortcut("Shift+SPACE")
        executes { view ->
            val player = view.context[ScorePlayer.CURRENT]
            val t = player.playHead.currentTime
            view.display(t, view.displayedDuration + t)
        }
    }
    addAction("Move playback cursor to start") {
        shortcut("Ctrl+Shift?+DIGIT0")
        icon(Material2MZ.SKIP_PREVIOUS)
        enableWhen { view -> view.context[ScorePlayer.CURRENT].isScheduled.not() }
        executes { view, ev ->
            val player = view.context[ScorePlayer.CURRENT]
            if (player.isScheduled.now) return@executes
            if (ev.isShiftDown()) {
                view.display(0.0.asTime, view.displayedDuration)
            }
            player.playHead.movePlayHeadToStart()
        }
    }
    addAction("Zoom in") {
        shortcut("Ctrl+PLUS")
        icon(Material2MZ.ZOOM_IN)
        executes { view ->
            view.zoom(1 / 1.2, view.mouseX)
        }
    }
    addAction("Zoom out") {
        shortcut("Ctrl+MINUS")
        icon(Material2MZ.ZOOM_OUT)
        executes { view ->
            view.zoom(1.2, view.mouseX)
        }
    }
    addAction("Display selected region") {
        shortcut("Alt?+Z")
        executes { view, ev ->
            if (ev.isTargetTextInput && !ev.isAltDown()) return@executes
            view.displaySelectedArea()
        }
    }
    addAction("Add flow group") {
        shortcut("Ctrl+Shift+F")
        executes { pane ->
            val anchor = Robot().mousePosition
            val y = pane.getScoreY(pane.screenToLocal(anchor).y)
            if (y !in zero..one) return@executes
            val flows = pane.context[AudioFlows]
            val name = NamePrompt(flows, "Name for new flow group", "")
                .showDialog(pane.scene.window, anchor) ?: return@executes
            val color = randomColor()
            val group = AudioFlowGroup.create(name, y, color)
            flows.add(group)
        }
    }
})