package ponticello.model.record

import fxutils.actions.action
import hextant.context.Context
import hextant.core.editor.ListenerManager
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import ponticello.model.obj.AbstractRenamableObject
import ponticello.model.obj.project
import ponticello.model.project.LIVE_BUFFERS
import ponticello.model.project.get
import ponticello.sc.client.SuperColliderClient
import ponticello.ui.record.LiveBufferViewConfig
import reaktive.value.ReactiveBoolean
import reaktive.value.ReactiveVariable
import reaktive.value.binding.`if`
import reaktive.value.now

@Serializable
class LiveBufferObject(
    private val source: CaptureSource,
    @SerialName("viewConfig") private var _viewConfig: LiveBufferViewConfig,
    @SerialName("enabled") private val _enabled: ReactiveVariable<Boolean>
) : AbstractRenamableObject() {
    @Transient
    private val listeners = ListenerManager.createWeakListenerManager<Listener>()

    override val registry: LiveBufferRegistry
        get() = context.project[LIVE_BUFFERS]

    @SerialName("name")
    override var _name: ReactiveVariable<String>? = null

    @Transient
    lateinit var buffer: AudioBuffer
        private set

    @Transient
    lateinit var peaks: WaveformPeaks
        private set

    @Transient
    private var capture: LiveAudioCapture? = null

    var viewConfig
        get() = _viewConfig
        set(value) {
            if (_viewConfig == value) return
            _viewConfig = value
            listeners.notifyListeners { changedViewType(value) }
        }

    val enabled: ReactiveBoolean get() = _enabled

    fun setEnabled(enabled: Boolean) {

    }

    override fun initialize(context: Context) {
        super.initialize(context)
        val sampleRate = context[SuperColliderClient].sampleRate
        buffer = HeapAudioBuffer(sampleRate, source.bufferSize, INITIAL_CAPACITY)
        peaks = WaveformPeaks(buffer, minZoom = 4, maxZoom = 16)
        val capture = source.capture(context)
        if (capture != null && enabled.now) {
            capture.start(buffer)
            this.capture = capture
        }
    }

    override fun onRemoved() {
        capture?.stop()
    }

    fun addListener(listener: Listener) {
        listeners.addListener(listener)
    }

    interface Listener {
        fun changedViewType(type: LiveBufferViewConfig)
    }

    companion object {
        val toggleEnabledAction = action<LiveBufferObject>("Enable/Disable") {
            toggles(LiveBufferObject::_enabled)
            icon(MaterialDesignR.RECORD)
            description { b -> `if`(b.enabled, then = { "Disable" }, otherwise = { "Enable" }) }
        }

        private const val INITIAL_CAPACITY = 1024 * 1024
    }
}