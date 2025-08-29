package ponticello.ui.score

import bundles.set
import fxutils.*
import fxutils.actions.collectActions
import fxutils.actions.registerActions
import hextant.context.extend
import javafx.scene.layout.HBox
import javafx.scene.paint.Color
import ponticello.model.live.LiveObjectRegistry
import ponticello.model.score.ScoreObject
import ponticello.model.score.ScoreObjectGroup
import ponticello.ui.actions.*
import ponticello.ui.live.LiveObjectRegistryPane
import ponticello.ui.misc.PlayHead
import reaktive.value.binding.map
import reaktive.value.binding.orElse
import reaktive.value.fx.asObservableValue

class ScoreObjectPlayerPane private constructor(val obj: ScoreObject) {
    private val context = obj.context.extend()
    private val liveScoreObject = context[LiveObjectRegistry].getOrCreateLiveScoreObject(obj)
    val playHead = liveScoreObject.playHead ?: PlayHead()
    val scorePane = SingleObjectScorePane(obj, context, playHead)
    val timeCodeView = TimeCodeView()

    init {
        val selector = ScoreObjectSelectionManager(context, scorePane)
        context[ScoreObjectSelectionManager] = selector //TODO really?
        scorePane.initialize()
        playHead.attachTo(scorePane, timeCodeView)
        setupPlayback()
        val ctx = ObjectActionContext.MultiObjectContext(selector)
        scorePane.registerShortcuts {
            SelectionRelatedActions.addShortcuts(this, context)
            registerActions(ScoreObjectActions.all.withContext(ctx))
            registerActions(actions.withContext(this@ScoreObjectPlayerPane))
        }
        val liveObject = context[LiveObjectRegistry].getLiveScoreObject(obj)
        if (liveObject != null) {
            liveObject.player.playHead.attachTo(scorePane, timeCodeView)
        }
        if (obj is ScoreObjectGroup) {
            scorePane.backgroundProperty().bind(
                obj.associatedColor.orElse(Color.BLACK).map(::background).asObservableValue()
            )
        }
    }

    private fun setupPlayback() {
        context[TimeCodeView] = timeCodeView
        context[ScoreObjectDuplicator].registerRootPane(scorePane)
    }

    fun createToolbar() = HBox(
        hspace(10.0),
        toolbarPart(actions.withContext(this)) styleClass "toolbar-part-segment",
        hspace(20.0),
        timeCodeView,
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

        private fun setupLiveScoreObject(
            pane: ScoreObjectPlayerPane,
        ) {
            val registry = pane.context[LiveObjectRegistry]
            val liveObject = pane.liveScoreObject
            if (!registry.has(liveObject)) {
                val positionInMainScore = pane.scorePane.positionInMainScore()
                if (positionInMainScore != null) {
                    liveObject.inferQuantizationFrom(positionInMainScore)
                }
                registry.add(liveObject)
            }
        }

        private val panes = mutableMapOf<ScoreObject, ScoreObjectPlayerPane>()

        fun hasPane(obj: ScoreObject) = panes.containsKey(obj)

        fun getPane(obj: ScoreObject): ScoreObjectPlayerPane = panes.getOrPut(obj) { ScoreObjectPlayerPane(obj) }
    }
}