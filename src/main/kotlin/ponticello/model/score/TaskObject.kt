package ponticello.model.score

import hextant.context.Context
import hextant.serial.EditorRoot
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.Decimal
import ponticello.impl.writeCode
import ponticello.model.instr.ParameterDefObject
import ponticello.model.player.ScorePlayer
import ponticello.model.score.controls.ParameterControl
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

    override val superColliderPrefix: String get() = "~task"

    override val canResizeHorizontally: Boolean
        get() = false

    override val canResizeVertically: Boolean
        get() = false

    @Transient
    private var synchronized = false

    @Transient
    private lateinit var synchronizer: Observer

    override fun doClone(): ScoreObject = TaskObject(code.clone(context))

    override fun initialize(context: Context) {
        super.initialize(context)
        code.initialize(context)
        synchronizer = code.editor.result.observe { _, _, _ ->
            synchronized = false
        }
    }

    override fun ScWriter.createObject() {
        appendBlock("$name = Task", endLine = false) {
//            +"s.latency.wait"
            val block = code.editor.result.now
            block.writeCode(writer, context)
            +"$name = nil"
        }
        synchronized = true
    }

    override fun startNewInstance(
        pos: ObjectPosition, cutoff: Decimal, instance: ScoreObjectInstance?,
        latency: Decimal, player: ScorePlayer, extraArguments: Map<ParameterDefObject, ParameterControl>
    ): String = writeCode(group = !synchronized) {
        if (!synchronized) {
            sync()
            synchronized = true
        }
        +"$superColliderName.play"
    }
}