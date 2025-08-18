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
import ponticello.model.registry.InstrumentRegistry
import ponticello.model.registry.ObjectRegistry
import ponticello.sc.ParameterType
import ponticello.sc.client.ScWriter
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.run
import ponticello.sc.code
import ponticello.sc.editor.CodeBlockEditor
import ponticello.sc.editor.ScExprExpander
import ponticello.ui.registry.ParameterDefList
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
class ProcessDefObject(
    override val color: ReactiveVariable<@Serializable(with = ColorSerializer::class) Color>,
    override val parameters: ParameterDefList,
    val setupBlock: EditorRoot<@Contextual CodeBlockEditor> = EditorRoot(CodeBlockEditor().defaultState()),
    val loopBlock: EditorRoot<@Contextual CodeBlockEditor> = EditorRoot(CodeBlockEditor().defaultState()),
    val deltaExpr: EditorRoot<@Contextual ScExprExpander> = EditorRoot(ScExprExpander().defaultState()),
) : ConfigurableInstrumentObject, AbstractSuperColliderObject() {
    override val superColliderName: String
        get() = "~proc_${name.now}"

    override val registry: ObjectRegistry<*>
        get() = context[InstrumentRegistry]

    override val canCopy: Boolean
        get() = true

    override fun supports(type: ParameterType): Boolean = true

    override fun copy(): ProcessDefObject = ProcessDefObject(
        color.copy(),
        ParameterDefList(parameters.mapTo(mutableListOf()) { p -> p.copy().withName(p.name.now) }),
        setupBlock.clone(context),
        loopBlock.clone(context)
    )

    override fun ScWriter.sync() {
        createObject()
    }

    override fun onUpdated() {}

    override fun ScWriter.createObject() {
        val setup = setupBlock.editor.result.now
        val loop = loopBlock.editor.result.now
        val delta = deltaExpr.editor.result.now

        appendBlock("$superColliderName = ") {
            append("arg player_id, t = 0, duration")
            for (p in parameters) {
                val defaultValue = p.spec.now.defaultValueExpr
                append(", ")
                val name = "${p.name.now}___"
                if (defaultValue != null) append("$name = $defaultValue")
                else append(name)
            }
            appendLine(";")
            if (parameters.isNotEmpty()) {
                +"var ${parameters.joinToString { p -> p.name.now }}"
            }
            for (p in parameters) {
                val name = p.name.now
                +"$name = ${name}___.value(0)"
            }
            setup.writeCode(writer, context)
            appendBlock("while { t <= duration }") {
                append("var delta___")
                for (variable in loop.variables) {
                    append(", ")
                    append(variable.text)
                }
                appendLine(";")
                for (p in parameters) {
                    val name = p.name.now
                    +"$name = ${name}___.value(t)"
                }
                for (statement in loop.statements) {
                    statement.code(writer, context)
                    appendLine("; ")
                }
                +"delta___ = ${delta.code(context)}"
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