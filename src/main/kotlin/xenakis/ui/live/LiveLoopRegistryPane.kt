package xenakis.ui.live

import fxutils.SubWindow
import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import javafx.event.Event
import javafx.geometry.Point2D
import javafx.scene.Parent
import org.kordamp.ikonli.codicons.Codicons
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.model.live.LiveLoopObject
import xenakis.model.live.LiveLoopRegistry
import xenakis.model.live.LoopConfig
import xenakis.model.score.Score
import xenakis.ui.registry.ObjectBox
import xenakis.ui.registry.ObjectListView.DisplayMode

class LiveLoopRegistryPane(registry: LiveLoopRegistry) : LiveObjectRegistryPane<LiveLoopObject>(registry) {
    init {
        setup()
    }

    override val supportedModes: Set<DisplayMode>
        get() = setOf(DisplayMode.SubWindow, DisplayMode.DetailsPane)

    override fun getContent(obj: LiveLoopObject, mode: DisplayMode): Parent = LiveLoopPane(obj)

    override fun configureSubWindow(window: SubWindow) {
        window.width = 400.0
        window.height = 400.0
    }

    override fun createNewObject(name: String, ev: Event?): LiveLoopObject? {
        val config = LoopConfig.createDefault()
        config.initialize(registry.context)
        val pane = LoopConfigDialog(config, "Configure new live loop '$name'")
        pane.showDialog(ev) ?: return null
        val score = Score()
        val obj = LiveLoopObject(reactiveVariable(name), config, score)
        return obj
    }

    override fun getActions(box: ObjectBox<LiveLoopObject>): List<ContextualizedAction> =
        super.getActions(box) + actions.withContext(box)

    companion object {
        private val actions = collectActions<ObjectBox<LiveLoopObject>> {
            addAction("Configure") {
                icon(Codicons.SYMBOL_PROPERTY)
                executes { box ->
                    val obj = box.obj
                    val copy = obj.config.copy()
                    copy.initialize(obj.context)
                    LoopConfigDialog(copy, "Configure live loop '${obj.name.now}")
                        .showDialog(anchorNode = box, offset = Point2D(box.width, 0.0)) ?: return@executes
                    obj.config.update(copy)
                    //TODO resize the score
                }
            }
        }
    }
}