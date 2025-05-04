package xenakis.ui.score

import bundles.set
import fxutils.actions.action
import fxutils.actions.makeButton
import fxutils.centerChildren
import fxutils.createBorder
import fxutils.hspace
import fxutils.infiniteSpace
import hextant.context.extend
import javafx.scene.control.SplitPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.stage.Screen
import org.kordamp.ikonli.materialdesign2.MaterialDesignV
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.times
import xenakis.model.player.ScorePlayer
import xenakis.model.score.ScoreObject
import xenakis.model.score.ScoreObjectGroup
import xenakis.ui.actions.PlaybackActions
import xenakis.ui.actions.toolbarPart
import xenakis.ui.registry.ScoreObjectRegistryPane

//TODO bad name
class ScoreObjectViewPane private constructor(val obj: ScoreObject) : VBox() {
    private lateinit var player: ScorePlayer
    private val context = obj.context.extend()
    private val scorePane = SingleObjectScorePane(obj, context)
    private val splitter = SplitPane(scorePane)
    private var lastDividerPosition = -1.0
    private val timeCodeView = TimeCodeView()
    private val showDetailsPane = reactiveVariable(false)

    init {
        setDefaultSize()
        border = createBorder(Color.AQUAMARINE, 2.0)
        scorePane.initialize()
        setupPlayback()
        setVgrow(splitter, Priority.ALWAYS)
        children.addAll(createPlaybackBar(), splitter)
    }

    private fun toggleDetailsPane() {
        if (!showDetailsPane.now) {
            val view = scorePane.getSingleObjectView() ?: return
            showDetailsPane.now = true
            val detailPane = view.getDetailPane()
            splitter.items.setAll(scorePane, detailPane)
            val dividerPosition = lastDividerPosition.takeIf { it != -1.0 } ?: 0.66
            splitter.setDividerPositions(dividerPosition)
        } else {
            lastDividerPosition = splitter.dividerPositions[0]
            splitter.items.setAll(scorePane)
            showDetailsPane.now = false
        }
    }

    fun setDefaultSize() {
        val screenBounds = Screen.getPrimary().visualBounds
        val width = (obj.duration * 40).value.coerceIn(500.0, screenBounds.width)
        val height = (obj.height * 500).value.coerceIn(500.0, screenBounds.height)
        scorePane.setPrefSize(width, height)
    }

    private fun setupPlayback() {
        context[TimeCodeView] = timeCodeView
        context[ScorePane.CURRENT_ROOT] = scorePane
        context[ScoreObjectDuplicator].registerRootPane(scorePane)
        player = ScorePlayer.create(scorePane, loopingActivated = obj.liveConfig.loop)
        context[ScorePlayer.CURRENT] = player
    }

    private fun createPlaybackBar() = HBox(
        infiniteSpace(),
        toolbarPart(ScoreObjectRegistryPane.actions.withContext(obj)),
        hspace(20.0),
        toolbarPart(PlaybackActions.withContext(player)),
        hspace(20.0),
        timeCodeView,
        infiniteSpace(),
        showDetailPaneAction.withContext(this).makeButton("medium-icon-button")
    ).centerChildren()

    companion object {
        private val panes = mutableMapOf<ScoreObject, ScoreObjectViewPane>()

        private val showDetailPaneAction = action<ScoreObjectViewPane>("Show detail pane") {
            icon(MaterialDesignV.VIEW_LIST_OUTLINE)
            applicableIf { pane -> pane.obj !is ScoreObjectGroup }
            toggleState { pane -> pane.showDetailsPane }
            executes { pane -> pane.toggleDetailsPane() }
        }

        fun getPane(obj: ScoreObject): ScoreObjectViewPane = panes.getOrPut(obj) { ScoreObjectViewPane(obj) }
    }
}