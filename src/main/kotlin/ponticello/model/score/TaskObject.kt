package ponticello.model.score

import bundles.set
import hextant.context.Context
import hextant.context.SelectionDistributor
import hextant.context.extend
import hextant.serial.EditorRoot
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.model.ctx.PonticelloContext
import ponticello.model.player.ObjectPlaybackInfo
import ponticello.sc.client.ScWriter
import ponticello.sc.editor.CodeBlockEditor
import reaktive.Observer
import reaktive.value.ReactiveVariable
import reaktive.value.now

@Serializable
@SerialName("Task")
class TaskObject(
    val code: EditorRoot<@Contextual CodeBlockEditor>,
) : ScoreObject() {
    @SerialName("name")
    override var _name: ReactiveVariable<String>? = null

    override val type: String
        get() = "task"

    override val canResizeHorizontally: Boolean
        get() = false

    override val canResizeVertically: Boolean
        get() = false

    @Transient
    private lateinit var synchronizer: Observer

    override fun doClone(): ScoreObject = TaskObject(code.clone(context))

    override fun initialize(context: Context) {
        super.initialize(context)
        code.initialize(context.extend {
            set(SelectionDistributor, SelectionDistributor.newInstance())
            set(PonticelloContext, PonticelloContext.Task(this@TaskObject))
        })
        synchronizer = code.editor.result.observe { _ ->
            isCreatedInSuperCollider = false
        }
    }

    override fun createInSuperCollider(writer: ScWriter) {
        writer.appendBlock("$name = Task", endLine = null) {
//            +"s.latency.wait"
            val block = code.editor.result.now
            block.writeCode(writer.writer, context)
            +"$name = nil"
        }
    }

    override fun ScWriter.startNewInstance(
        info: ObjectPlaybackInfo
    ) {
        +"$superColliderName.play"
    }
}