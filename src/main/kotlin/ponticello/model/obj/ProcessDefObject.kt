package ponticello.model.obj

import bundles.set
import hextant.context.Context
import hextant.context.SelectionDistributor
import hextant.context.extend
import hextant.core.editor.defaultState
import hextant.serial.EditorRoot
import javafx.scene.paint.Color
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import ponticello.impl.ColorSerializer
import ponticello.impl.Logger
import ponticello.impl.copy
import ponticello.impl.randomColor
import ponticello.model.registry.ObjectRegistry
import ponticello.model.registry.ProcessDefRegistry
import ponticello.sc.CodeBlock
import ponticello.sc.RawScExpr
import ponticello.sc.client.ScWriter
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.run
import ponticello.sc.editor.CodeBlockEditor
import ponticello.sc.editor.ScExprExpander
import ponticello.sc.substitute
import ponticello.ui.registry.ParameterDefList
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
class ProcessDefObject(
    val color: ReactiveVariable<@Serializable(with = ColorSerializer::class) Color>,
    override val parameters: ParameterDefList,
    val setupBlock: EditorRoot<@Contextual CodeBlockEditor> = EditorRoot(CodeBlockEditor().defaultState()),
    val loopBlock: EditorRoot<@Contextual CodeBlockEditor> = EditorRoot(CodeBlockEditor().defaultState()),
    val deltaExpr: EditorRoot<@Contextual ScExprExpander> = EditorRoot(ScExprExpander().defaultState()),
) : ConfigurableParameterizedObjectDef, AbstractSuperColliderObject() {
    override val superColliderName: String
        get() = "~proc_${name.now}"

    override val registry: ObjectRegistry<*>
        get() = context[ProcessDefRegistry]

    override val canCopy: Boolean
        get() = true

    override fun copy(): ProcessDefObject = ProcessDefObject(
        color.copy(),
        ParameterDefList(parameters.mapTo(mutableListOf()) { p -> p.copy() }),
        setupBlock.clone(context),
        loopBlock.clone(context)
    )

    override fun ScWriter.sync() {
        createObject()
    }

    override fun ScWriter.createObject() {
        //TODO variables set inside the loop aren't updated outside - how to change this?
        val argumentSubstitution = parameters.associate { p ->
            val name = p.name.now
            name to { RawScExpr("$name.value(t)") }
        }
        val setup = setupBlock.editor.result.now.substitute(argumentSubstitution) as CodeBlock
        val loop = loopBlock.editor.result.now
        val delta = deltaExpr.editor.result.now

        val loopFunctionName = "${superColliderName}_loop"
        val variables = setup.variables.map { v -> v.text }
        appendBlock("$loopFunctionName = ") {
            append("arg t, duration")
            for (param in parameters.map { p -> p.name.now } + variables) {
                append(", $param")
            }
            appendLine(";")
            loop.writeCode(writer, context)
            delta.code(writer, context)
        }

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
            setup.writeCode(writer, context)
            appendBlock("while { t <= duration }") {
                val arguments = parameters.map { p ->
                    val name = p.name.now
                    "$name.value(t)"
                } + variables
                +"var delta___ = $loopFunctionName.value(t, duration, ${arguments.joinToString()})"
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
            color = reactiveVariable(randomColor()),
            parameters = ParameterDefList(),
            setupBlock = EditorRoot(CodeBlockEditor().defaultState()),
            loopBlock = EditorRoot(CodeBlockEditor().defaultState())
        ).withName(name)

        fun unresolved() = newEmpty("<unresolved>")
    }
}