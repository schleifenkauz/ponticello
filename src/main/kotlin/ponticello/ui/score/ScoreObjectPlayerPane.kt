package ponticello.ui.score

import fxutils.actions.collectActions
import fxutils.centerChildren
import fxutils.hspace
import fxutils.styleClass
import javafx.scene.layout.HBox
import ponticello.model.live.LiveObjectRegistry
import ponticello.model.score.ScoreObject
import ponticello.ui.actions.PlaybackActions
import ponticello.ui.actions.toolbarPart
import ponticello.ui.live.LiveObjectRegistryPane
import ponticello.ui.misc.PlayHead
import reaktive.value.now

class ScoreObjectPlayerPane private constructor(val obj: ScoreObject): ScoreObject.Listener {
    private val context = obj.context
    private val liveScoreObject = context[LiveObjectRegistry].getOrCreateLiveScoreObject(obj)

    val playHead = liveScoreObject.playHead ?: PlayHead()
    val scorePane = SingleObjectScorePane(obj, context, playHead, paintGrid = true)

    init {
        scorePane.initialize()
        obj.addListener(this)
        setupActionHandlers()
    }

    private fun setupActionHandlers() {
        context[ScoreObjectDuplicator].registerRootPane(scorePane)
    }

    override fun resizedObject(obj: ScoreObject) {
        scorePane.repaint()
    }

    fun createToolbar() = HBox(
        hspace(10.0),
        toolbarPart(actions.withContext(this)) styleClass "toolbar-part-segment",
        hspace(20.0),
        scorePane.timeCodeView,
        hspace(10.0)
    ).centerChildren()

    companion object {
        private val actions = collectActions<ScoreObjectPlayerPane> {
            add(PlaybackActions.goToStartAction("Ctrl+DIGIT0")) { p -> p.playHead }
            add(LiveObjectRegistryPane.playPauseAction.map { p -> p.liveScoreObject }) {
                executesFirst { pane, _ -> setupLiveScoreObject(pane) }
            }
            add(LiveObjectRegistryPane.configureQuantizationAction.map { p -> p.liveScoreObject }) {
                applicableIf { pane -> pane.obj.affectsPlayback }
                executesFirst { pane, _ -> setupLiveScoreObject(pane) }
            }
            add(LiveObjectRegistryPane.toggleLoopingAction.map { p -> p.liveScoreObject }) {
                applicableIf { pane -> pane.obj.affectsPlayback }
                executesFirst { pane, _ -> setupLiveScoreObject(pane) }
            }
        }

        private fun setupLiveScoreObject(pane: ScoreObjectPlayerPane) {
            val registry = pane.context[LiveObjectRegistry]
            val liveObject = pane.liveScoreObject
            if (!registry.has(liveObject)) {
                val positionInMainScore = pane.scorePane.positionInMainScore()
                if (positionInMainScore != null) {
                    liveObject.inferQuantizationFrom(positionInMainScore, pane.context)
                    liveObject.absoluteScoreY.now = positionInMainScore.y
                }
                liveObject.playHead = pane.playHead
                registry.add(liveObject)
            }
        }

        private val panes = mutableMapOf<ScoreObject, ScoreObjectPlayerPane>()

        fun hasPane(obj: ScoreObject) = panes.containsKey(obj)

        fun getPane(obj: ScoreObject): ScoreObjectPlayerPane = panes.getOrPut(obj) { ScoreObjectPlayerPane(obj) }
    }
}