package xenakis.model

import hextant.context.Context
import javafx.scene.input.DataFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.event.EventStream
import reaktive.event.unitEvent
import reaktive.value.ReactiveInt
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.SuperColliderClient
import xenakis.impl.superColliderPath
import xenakis.sc.editor.AbstractRenamableObject
import java.io.File
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

@Serializable
sealed class BufferObject : AbstractRenamableObject() {
    open val variableName get() = "~buf_${name.now}"

    abstract val initializationCode: String

    abstract val channels: ReactiveInt

    abstract val frames: ReactiveInt

    @Transient
    protected val contentChange = unitEvent()

    val contentsChanged: EventStream<Unit> get() = contentChange.stream

    override fun canRenameTo(newName: String): Boolean = !context[BufferRegistry].has(newName)

    override fun rename(newName: String) {
        val oldVariableName = variableName
        super.rename(newName)
        context[SuperColliderClient].run("$variableName = $oldVariableName; $oldVariableName = nil;")
    }

    abstract fun sync(client: SuperColliderClient)

    protected fun reallocate() {
        context[SuperColliderClient].run {
            +"if ($variableName != nil) { $variableName.free; }"
            +initializationCode
        }
    }

    protected open fun getAudioStream(): AudioInputStream? {
        val file = File.createTempFile("buffer_contents", ".wav")
        context[SuperColliderClient].eval("$variableName.write(${file.superColliderPath}, 'wav', 'int16');")
        //TODO somehow wait for the buffer to be written to disk
        return AudioSystem.getAudioInputStream(file)
    }

    fun <T> useAudioStream(block: (AudioInputStream?) -> T): T {
        val stream = getAudioStream()
        return if (stream == null) block(null) else stream.use(block)
    }

    override fun initialize(context: Context) {
        if (initialized) return
        super.initialize(context)
        sync(context[SuperColliderClient])
    }

    open fun onRemove() {
        if (!initialized) return
        context[SuperColliderClient].run("$variableName.free; $variableName = nil;")
    }

    override fun createReference(): BufferObjectReference = BufferObjectReference(this)

    enum class Type {
        File, Allocate, Reference
    }

    companion object {
        val DATA_FORMAT = DataFormat("buffer")

        val defaultBuffer = ReferencedBuffer(reactiveVariable("0"))
    }
}