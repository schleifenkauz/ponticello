package ponticello.ui.record

import fxutils.*
import fxutils.actions.*
import fxutils.controls.CheckBox
import fxutils.controls.IntSpinner
import fxutils.drag.TypedDataFormat
import javafx.geometry.Side.BOTTOM
import javafx.scene.Parent
import javafx.scene.control.Label
import javafx.scene.input.TransferMode
import javafx.scene.layout.HBox
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignD
import org.kordamp.ikonli.materialdesign2.MaterialDesignM
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import org.kordamp.ikonli.materialdesign2.MaterialDesignS
import ponticello.impl.rangeTo
import ponticello.impl.toDecimal
import ponticello.impl.zero
import ponticello.model.obj.LiveBufferReference
import ponticello.model.project.LIVE_BUFFERS
import ponticello.model.project.PonticelloProject
import ponticello.model.project.get
import ponticello.model.record.AudioCapture
import ponticello.model.record.LiveBufferObject
import ponticello.model.record.LiveBufferRegistry
import ponticello.model.record.LoudnessThreshold
import ponticello.model.registry.ObjectList
import ponticello.model.registry.ObjectReference
import ponticello.ui.controls.Knob
import ponticello.ui.dock.Side
import ponticello.ui.dock.TabbedToolPane
import ponticello.ui.dock.ToolPane
import reaktive.value.binding.greaterThan
import reaktive.value.now

class LiveBuffersPane(
    private val buffers: LiveBufferRegistry
) : TabbedToolPane<LiveBufferObject>(buffers), ObjectList.Listener<LiveBufferObject> {
    override val type: Type get() = LiveBuffersPane
    private var selectedObject: LiveBufferReference = ObjectReference.none()

    private val addBufferButton = addItemButton.withContext(this).makeButton("medium-icon-button")

    private val controlBar = HBox(5.0).centerChildren().alwaysHGrow()

    override val headerContent = HBox(
        5.0, super.headerContent, addBufferButton, controlBar
    ).centerChildren().alwaysHGrow()

    override fun doSetup() {
        super.doSetup()
        registerShortcuts {
            val view = content as? LiveAudioBufferView ?: return@registerShortcuts
            registerActions(playbackActions.withContext(view))
            registerActions(LiveBufferObject.actions.withContext(view.bufferObject))
        }
    }

    override fun createItemBox(obj: LiveBufferObject): HBox {
        val label = label(obj.name)
        val toggleBtn = LiveBufferObject.toggleEnabledAction.withContext(obj).makeButton("medium-icon-button")
        toggleBtn.setOnDragDetected { ev ->
            val db = toggleBtn.startDragAndDrop(TransferMode.COPY)
            db.setContent(mapOf(TOGGLE_RECORD to obj.name.now))
            ev.consume()
        }
        val box = HBox(toggleBtn, label) styleClass "live-buffer-item"
        val contextMenuButton = showContextMenu.withContext(Pair(obj, box)).makeButton("medium-icon-button")
        box.children.add(contextMenuButton)
        return box
    }

    override fun selected(obj: LiveBufferObject?) {
        controlBar.children.clear()
        if (obj != null) {
            val view = content(obj) as LiveAudioBufferView
            controlBar.children.addAll(
                infiniteSpace(),
                ActionBar(playbackActions.withContext(view), "medium-icon-button"),
                hspace(15.0),
                CheckBox(obj.threshold.isEnabled, "Threshold:"),
                Knob("Threshold", obj.threshold.db, LoudnessThreshold.SPEC, 12.0),
                hspace(15.0),
                Label("Block size:"),
                IntSpinner(obj.threshold.blockSize, 1024..1024 * 8, 1024)
            )
        }
    }

    override fun getContent(obj: LiveBufferObject): Parent =
        LiveAudioBufferView(obj, initialDisplayRange = zero..10.toDecimal()).alwaysHGrow()

    companion object : Type(uid = 18, "LiveBuffers") {
        override val defaultSide: Side
            get() = Side.BOTTOM

        override val icon: Ikon get() = MaterialDesignM.MICROPHONE

        override fun createToolPane(project: PonticelloProject): ToolPane = LiveBuffersPane(project[LIVE_BUFFERS])

        val TOGGLE_RECORD = TypedDataFormat<LiveBufferReference>("ponticello:toggle-record")

        private val showContextMenu = action<Pair<LiveBufferObject, HBox>>("Options") {
            icon(MaterialDesignD.DOTS_VERTICAL)
            executes { (buffer, box), _ ->
                contextMenu(LiveBufferObject.actions.withContext(buffer)).show(box, BOTTOM, 0.0, 0.0)
            }
        }

        private val addItemButton = action<LiveBuffersPane>("Add buffer") {
            icon(MaterialDesignP.PLUS_CIRCLE)
            executes { pane, ev ->
                val buffer = NewLiveBufferPrompt(pane.buffers).showDialog(ev) ?: return@executes
                pane.items.add(buffer)
                pane.select(buffer)
            }
        }

        private val playbackActions = collectActions<LiveAudioBufferView> {
            addAction("Play selected range") {
                shortcut("Ctrl+SPACE")
                executes { view ->
                    val selectedRange = view.selectedRange
                    if (selectedRange.isEmpty()) return@executes
                    view.playBufferRange(selectedRange)
                }
            }
            addAction("Stop buffer playback") {
                shortcut("Ctrl+Shift+PERIOD")
                icon(MaterialDesignS.STOP)
                enableWhen { view -> view.cursorCount.greaterThan(0) }
                executes { view -> view.stopPlayback() }
            }
        }

        val toggleRecording = action<LiveBuffersPane>("Toggle recording") {
            shortcut("Ctrl+Shift+R")
            applicableIf { pane -> pane.isShowing.now }
            executes { pane ->
                val selectedBuffer = pane.selectedObject.get() ?: return@executes
                val recording = selectedBuffer.enabled.now
                val validStatus = setOf(AudioCapture.Status.PREPARED, AudioCapture.Status.RUNNING)
                if (selectedBuffer.capture.status.now in validStatus) {
                    selectedBuffer.setEnabled(!recording)
                }
            }
        }
    }
}