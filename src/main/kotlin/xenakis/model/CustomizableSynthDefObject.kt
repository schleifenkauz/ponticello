package xenakis.model

import hextant.serial.EditorRoot
import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.list.MutableReactiveList
import reaktive.value.ReactiveVariable
import reaktive.value.now
import xenakis.impl.ColorSerializer
import xenakis.impl.SuperColliderClient
import xenakis.impl.SuperColliderContext
import xenakis.sc.*
import xenakis.sc.editor.AbstractRenamableObject
import xenakis.sc.editor.CodeBlockEditor

@Serializable
class CustomizableSynthDefObject(
    override val mutableName: ReactiveVariable<String>,
    override val color: ReactiveVariable<@Serializable(with = ColorSerializer::class) Color>,
    override val parameters: MutableReactiveList<ParameterDefObject>,
    val ugenGraph: EditorRoot<CodeBlockEditor>
) : SynthDefObject, AbstractRenamableObject() {
    @Transient
    private lateinit var registry: SynthDefRegistry

    override fun initialize(registry: SynthDefRegistry) {
        this.registry = registry
    }

    override fun SuperColliderClient.sync() {
        addToGlobalSynthDescLib()
    }

    override fun SuperColliderClient.removeSynthDef() {
        send("removeSynthDef", listOf(name))
    }

    override fun canRenameTo(newName: String): Boolean = !registry.hasSynthDef(newName)

    override fun rename(newName: String) {
        val client = registry.context[SuperColliderClient]
        client.removeSynthDef()
        super.rename(newName)
        client.addToGlobalSynthDescLib()
    }

    fun SuperColliderContext.addToGlobalSynthDescLib() = run {
        append("SynthDef(\\${name.now}, ")
        val parameterVariables = parameters.now.map { p -> Identifier(p.name.now) }
        val parameterAssignments = parameters.now.map { p ->
            val parameterCode = RawScExpr("\\${p.name.now}.${p.spec.now.code}")
            Assignment(Identifier(p.name.now), parameterCode)
        }
        val block = CodeBlock(
            variables = parameterVariables + ugenGraph.editor.result.now.variables,
            statements = parameterAssignments + ugenGraph.editor.result.now.statements
        )
        val graphFunc = ScFunction(emptyList(), block)
        graphFunc.code(this)
        appendLine(").add;")
    }
}