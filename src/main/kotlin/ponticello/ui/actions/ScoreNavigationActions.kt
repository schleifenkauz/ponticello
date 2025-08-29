package ponticello.ui.actions

import fxutils.actions.Action
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
import ponticello.ui.controls.NamePrompt
import ponticello.ui.score.NavigableScorePane
import ponticello.ui.score.RectangleSelection
import ponticello.ui.score.ScoreObjectSelectionManager
import reaktive.value.now

object ScoreNavigationActions : Action.Collector<NavigableScorePane>({
    addAction("Move View To Start") {
        description("Moves the displayed portion of the score to the start")
        shortcut("HOME")
        executes { pane -> pane.display(0.0.asTime, pane.displayedDuration) }
    }
    addAction("Display Whole Score") {
        shortcut("Ctrl+HOME")
        executes { pane -> pane.displayWholeScore() }
    }
    addAction("Move to Cursor") {
        description("Move displayed portion of the score to playback cursor")
        shortcut("Shift+SPACE")
        executes { pane ->
            val t = pane.playHead.currentTime
            pane.display(t, pane.displayedDuration + t)
        }
    }
    addAction("Move playback cursor to start") {
        shortcut("Ctrl+Shift?+DIGIT0")
        icon(Material2MZ.SKIP_PREVIOUS)
        enableWhen { pane -> pane.playHead.canMoveManually }
        executes { pane, ev ->
            if (!pane.playHead.canMoveManually.now) return@executes
            if (ev.isShiftDown()) {
                pane.display(0.0.asTime, pane.displayedDuration)
            }
            pane.playHead.movePlayHeadToStart()
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
        executes { pane ->
            pane.zoom(1.2, pane.mouseX)
        }
    }
    addAction("Display selected region") {
        shortcut("Z")
        executes { pane, ev ->
            if (ev.isTargetTextInput) return@executes
            val selectedArea = RectangleSelection.get()
            if (selectedArea != null) {
                RectangleSelection.clear()
                val offset = selectedArea.pane.absolutePosition
                val t = offset.time + selectedArea.time
                pane.display(t, t + selectedArea.duration)
            } else {
                val focusedView = pane.context[ScoreObjectSelectionManager].focusedView.now ?: return@executes
                val start = focusedView.absolutePosition.time
                val end = start + focusedView.instance.duration
                pane.display(start, end)
            }
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