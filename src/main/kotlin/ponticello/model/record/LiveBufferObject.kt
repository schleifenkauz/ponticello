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
import javax.sound.sampled.AudioFormat

@Serializable
class LiveBufferObject(
    private val source: CaptureSource,
    private val channelConfig: ChannelConfiguration,
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
    lateinit var buffer: MultiChannelAudioBuffer
        private set

    @Transient
    private var capture: AudioCapture? = null

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
            capture?.start()
        } else {
            capture?.stop()
        }
    }

    val format: AudioFormat
        get() = AudioFormat(buffer.sampleRate.toFloat(), 16, channelConfig.outputChannels, true, false)

    override fun initialize(context: Context) {
        super.initialize(context)
        val sampleRate = context[SuperColliderClient].sampleRate
        buffer = MultiChannelHeapAudioBuffer(
            channelConfig.outputChannels, sampleRate, INITIAL_CAPACITY
        )
        val capture = source.capture(context)
        if (capture != null) {
            capture.prepare(buffer, channelConfig)
            if (capture.status.now == AudioCapture.Status.PREPARED && enabled.now) {
                capture.start()
            }
            this.capture = capture
        }
    }

    override fun onRemoved() {
        capture?.close()
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
            executes { b -> b.setEnabled(!b.enabled.now) }
        }

        private const val INITIAL_CAPACITY = 1024 * 1024
    }
}