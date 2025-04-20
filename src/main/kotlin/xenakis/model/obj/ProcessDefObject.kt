package xenakis.model.obj

import bundles.set
import hextant.context.Context
import hextant.context.SelectionDistributor
import hextant.context.extend
import hextant.core.editor.defaultState
import hextant.serial.EditorRoot
import javafx.scene.paint.Color
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import reaktive.event.EventStream
import reaktive.event.unitEvent
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.ColorSerializer
import xenakis.impl.Logger
import xenakis.impl.copy
import xenakis.impl.randomColor
import xenakis.model.registry.ObjectRegistry
import xenakis.model.registry.ProcessDefRegistry
import xenakis.sc.CodeBlock
import xenakis.sc.RawScExpr
import xenakis.sc.client.ScWriter
import xenakis.sc.client.SuperColliderClient
import xenakis.sc.editor.CodeBlockEditor
import xenakis.sc.editor.ScExprExpander
import xenakis.sc.substitute
import xenakis.ui.registry.ParameterDefList

@Serializable
class ProcessDefObject(
    @SerialName("name") override val mutableName: ReactiveVariable<String>,
    val color: ReactiveVariable<@Serializable(with = ColorSerializer::class) Color>,
    override val parameters: ParameterDefList,
    val setupBlock: EditorRoot<@Contextual CodeBlockEditor> = EditorRoot(CodeBlockEditor().defaultState()),
    val loopBlock: EditorRoot<@Contextual CodeBlockEditor> = EditorRoot(CodeBlockEditor().defaultState()),
    val deltaExpr: EditorRoot<@Contextual ScExprExpander> = EditorRoot(ScExprExpander().defaultState())
) : ConfigurableParameterizedObjectDef, AbstractSuperColliderObject() {
    val update = unitEvent()

    override val updated: EventStream<Unit>
        get() = update.stream

    override val superColliderName: String
        get() = "~proc_${name.now}"

    override val registry: ObjectRegistry<*>
        get() = context[ProcessDefRegistry]

    override val canCopy: Boolean
        get() = true

    override fun copy(name: String): ProcessDefObject = ProcessDefObject(
        reactiveVariable(name),
        color.copy(),
        ParameterDefList(parameters.mapTo(mutableListOf()) { p -> p.copy() }),
        setupBlock.clone(context),
        loopBlock.clone(context)
    )

    override fun ScWriter.sync() {
        appendBlock("fork") {
            createObject()
            +"s.sync"
            +"~xenakis_addr.sendMsg('/update', 'process_def:${name.now}')"
        }
    }

    override fun ScWriter.createObject() {
        appendBlock("$superColliderName = ") {
            append("arg t = 0, duration")
            for (p in parameters) {
                val defaultValue = p.spec.now.defaultValueExpr
                val name = p.name.now
                append(", ")
                if (defaultValue != null) append("$name = $defaultValue")
                else append(name)
            }
            appendLine(";")
            val argumentSubstitution = parameters.associate { p ->
                val name = p.name.now
                name to RawScExpr("$name.value(t)")
            }
            val setup = setupBlock.editor.result.now.substitute(argumentSubstitution) as CodeBlock
            val loop = loopBlock.editor.result.now.substitute(argumentSubstitution) as CodeBlock
            val delta = deltaExpr.editor.result.now.substitute(argumentSubstitution)
            setup.writeCode(writer, context)
            appendBlock("while { t <= duration }") {
                appendBlock("var delta___ = ", endLine = false) {
                    loop.writeCode(writer, context)
                    delta.code(writer, context)
                }
                appendLine(".value;")
                +"delta___.wait"
                +"t = t + delta___"
            }
        }
    }

    override fun sync() {
        context[SuperColliderClient].run { writer.sync() }
        Logger.confirm("Synchronized ProcessDef '${name.now}'", Logger.Category.Instruments)
    }

    override fun ScWriter.freeObject() {
        +"$superColliderName = nil"
    }

    override fun initialize(context: Context) {
        if (initialized) return
        val myContext = context.extend {
            set(SelectionDistributor, SelectionDistributor.newInstance())
        }
        super.initialize(myContext)
        parameters.initialize(myContext)
        setupBlock.initialize(myContext)
        loopBlock.initialize(myContext)
        deltaExpr.initialize(myContext)
    }

    override fun rename(newName: String) {
        super.rename(newName)
        sync()
    }

    companion object {
        fun newEmpty(name: String) = ProcessDefObject(
            mutableName = reactiveVariable(name),
            color = reactiveVariable(randomColor()),
            parameters = ParameterDefList(),
            setupBlock = EditorRoot(CodeBlockEditor().defaultState()),
            loopBlock = EditorRoot(CodeBlockEditor().defaultState())
        )

        fun unresolved(context: Context) = newEmpty("<unresolved>")
    }
}