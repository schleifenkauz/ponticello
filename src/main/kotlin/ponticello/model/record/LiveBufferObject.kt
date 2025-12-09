package ponticello.model.record

import fxutils.actions.action
import fxutils.actions.collectActions
import fxutils.prompt.YesNoPrompt
import hextant.context.Context
import hextant.core.editor.ListenerManager
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.kordamp.ikonli.materialdesign2.MaterialDesignD
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import ponticello.model.obj.AbstractRenamableObject
import ponticello.model.obj.project
import ponticello.model.project.LIVE_BUFFERS
import ponticello.model.project.get
import ponticello.sc.client.SuperColliderClient
import ponticello.ui.controls.NamePrompt
import ponticello.ui.record.LiveBufferViewConfig
import reaktive.value.ReactiveBoolean
import reaktive.value.ReactiveVariable
import reaktive.value.binding.`if`
import reaktive.value.now
import javax.sound.sampled.AudioFormat

@Serializable
class LiveBufferObject(
    val source: CaptureSource,
    private val channelConfig: ChannelConfiguration,
    @SerialName("viewConfig") private var _viewConfig: LiveBufferViewConfig,
    @SerialName("enabled") private val _enabled: ReactiveVariable<Boolean>,
    val threshold: LoudnessThreshold = LoudnessThreshold.default()
) : AbstractRenamableObject() {
    @Transient
    private val listeners = ListenerManager.createWeakListenerManager<Listener>()

    override val registry: LiveBufferRegistry
        get() = context.project[LIVE_BUFFERS]

    @SerialName("name")
    override var _name: ReactiveVariable<String>? = null

    @Transient
    lateinit var buffer: MultiChannelAudioBuffer
        private set

    @Transient
    lateinit var capture: AudioCapture
        private set

    var viewConfig
        get() = _viewConfig
        set(value) {
            if (_viewConfig == value) return
            _viewConfig = value
            listeners.notifyListeners { changedViewType(value) }
        }

    val enabled: ReactiveBoolean get() = _enabled

    fun setEnabled(enabled: Boolean) {
        _enabled.set(enabled)
        if (enabled) {
            capture.start()
        } else {
            capture.stop()
        }
    }

    fun toggleEnabled() {
        setEnabled(!enabled.now)
    }

    val format: AudioFormat
        get() = AudioFormat(buffer.sampleRate.toFloat(), 16, channelConfig.outputChannels, true, false)

    override fun initialize(context: Context) {
        super.initialize(context)
        val sampleRate = context[SuperColliderClient].sampleRate
        buffer = MultiChannelHeapAudioBuffer(channelConfig.outputChannels, sampleRate, INITIAL_CAPACITY)
//        val file = context.project.projectDirectory.resolve("live_buffers/${name.now}.wav")
//        file.parentFile.mkdirs()
//        buffer = MultiChannelDiskAudioBuffer(file, sampleRate, channelConfig.outputChannels)
        capture = source.capture(context)
        if (capture !is NoAudioCapture) {
            capture.prepare(buffer, channelConfig, threshold)
            if (capture.status.now == AudioCapture.Status.PREPARED && enabled.now) {
                capture.start()
            }
        }
    }

    override fun dispose() {
        capture.close()
    }

    override fun deactivate() {
        dispose()
    }

    fun addListener(listener: Listener) {
        listeners.addListener(listener)
    }

    interface Listener {
        fun changedViewType(type: LiveBufferViewConfig)
    }

    companion object {
        val toggleEnabledAction = action<LiveBufferObject>("Enable/Disable") {
            icon { b ->
                `if`(
                    b.enabled,
                    then = { MaterialDesignR.RECORD },
                    otherwise = { MaterialDesignR.RECORD_CIRCLE_OUTLINE })
            }
            description { b -> `if`(b.enabled, then = { "Disable" }, otherwise = { "Enable" }) }
            executes { b -> b.toggleEnabled() }
        }

        val actions = collectActions<LiveBufferObject> {
            addAction("Clear buffer") {
                shortcut("Alt+DELETE")
                executes { obj, ev ->
                    val clear = YesNoPrompt("Clear buffer?").showDialog(ev)
                    if (clear == true) {
                        obj.buffer.clear()
                    }
                }
            }
            addAction("Delete buffer") {
                shortcut("Ctrl+DELETE")
                icon(MaterialDesignD.DELETE)
                executes { obj, ev ->
                    val delete = YesNoPrompt("Delete buffer?").showDialog(ev)
                    if (delete == true) {
                        obj.registry.remove(obj)
                    }
                }
            }
            addAction("Rename") {
                shortcut("F2")
                icon(MaterialDesignR.RENAME_BOX)
                executes { obj, ev ->
                    val newName = NamePrompt(obj.registry, "New name", obj.name.now)
                        .showDialog(ev)
                    if (newName != null) {
                        obj.rename(newName)
                    }
                }
            }
        }

        private const val INITIAL_CAPACITY = 1024 * 1024
    }
}