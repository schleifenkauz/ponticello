package ponticello.model.instr

import bundles.getOrNull
import bundles.set
import hextant.context.Context
import hextant.context.SelectionDistributor
import hextant.context.extend
import hextant.core.editor.defaultState
import hextant.serial.EditorRoot
import javafx.scene.paint.Color
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import ponticello.impl.Logger
import ponticello.model.ctx.KeywordVariable
import ponticello.model.ctx.ParameterDefVariable
import ponticello.model.ctx.PonticelloContext
import ponticello.model.ctx.Scope
import ponticello.model.obj.AbstractSuperColliderObject
import ponticello.model.obj.withName
import ponticello.sc.*
import ponticello.sc.client.ScWriter
import ponticello.sc.editor.CodeBlockEditor
import ponticello.ui.registry.ParameterDefList
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
class MidiEffectInstrument(
    override val parameters: ParameterDefList,
    val start: EditorRoot<@Contextual CodeBlockEditor>,
    val stop: EditorRoot<@Contextual CodeBlockEditor>,
    val noteOn: EditorRoot<@Contextual CodeBlockEditor>,
    val noteOff: EditorRoot<@Contextual CodeBlockEditor>,
    val cc: EditorRoot<@Contextual CodeBlockEditor>,
) : ConfigurableInstrumentObject, AbstractSuperColliderObject() {
    override val color: ReactiveVariable<Color>
        get() = reactiveVariable(Color.BLACK)

    override val instrumentType: String
        get() = "MIDI Effect"

    override var _name: ReactiveVariable<String>? = null

    override fun supports(type: ParameterType): Boolean = when (type) {
        ParameterType.BufferPosition -> false
        ParameterType.Trig -> false
        else -> true
    }

    override fun initialize(context: Context) {
        super.initialize(context)
        val parent = context.getOrNull(Scope)
        val scope = Scope.fromList(parameters, parent, ::ParameterDefVariable)
        for (param in CALLBACK_PARAMETERS) {
            scope.add(KeywordVariable(param))
        }
        val subContext = context.extend {
            set(SelectionDistributor, SelectionDistributor.newInstance())
            set(PonticelloContext, PonticelloContext.MidiEffect(this@MidiEffectInstrument))
            set(Scope, scope)
        }
        parameters.initialize(subContext)
        for (component in listOf(start, stop, noteOn, noteOff, cc)) {
            component.initialize(subContext)
        }
    }

    override fun ScWriter.sync() {
        createObject()
        Logger.confirm("Synchronized MidiEffectInstrument '${name.now}'", Logger.Category.Instruments)
    }

    override fun ScWriter.freeObject() {
        +"MidiEffectInstrument.remove('${name.now}')"
    }

    override fun onRename(oldName: String, newName: String) {
        client.run("MidiEffectInstrument.rename('$oldName', '$newName')")
    }

    override fun superColliderName(objectName: String): String = "MidiEffectInstrument.get('${objectName}')"

    private fun CodeBlock.subst(parameterMap: Map<String, () -> ScExpr>): CodeBlock {
        return substitute(parameterMap) as CodeBlock
    }

    override fun ScWriter.createObject() {
        val parameterMap = parameters.associate { p ->
            p.name.now to { Identifier("controls").send("get", SymbolLiteral(p.name.now)) }
        }
        appendGroup("MidiEffectInstrument") {
            appendLine("'${name.now}',")
            append(createDefaultValueMap())
            appendLine(",")
            appendBlock("start: ", endLine = ",") {
                +"arg track, controls"
                start.editor.result.now.subst(parameterMap).writeCode(writer, context)
            }
            appendBlock("stop: ", endLine = ",") {
                +"arg track, controls"
                stop.editor.result.now.subst(parameterMap).writeCode(writer, context)
            }
            appendBlock("noteOn: ", endLine = ",") {
                callbackParameters()
                noteOn.editor.result.now.subst(parameterMap).writeCode(writer, context)
            }
            appendBlock("noteOff: ", endLine = ",") {
                callbackParameters()
                noteOff.editor.result.now.subst(parameterMap).writeCode(writer, context)
            }
            appendBlock("cc: ", endLine = ",") {
                callbackParameters()
                cc.editor.result.now.subst(parameterMap).writeCode(writer, context)
            }
        }
    }

    private fun ScWriter.callbackParameters() {
        append("arg ")
        appendList(CALLBACK_PARAMETERS, ", ")
        appendLine(";")
    }

    companion object {
        private val CALLBACK_PARAMETERS = listOf("pitch", "velocity", "track", "controls", "src")

        fun newEmpty(name: String): MidiEffectInstrument = MidiEffectInstrument(
            parameters = ParameterDefList(),
            start = EditorRoot(CodeBlockEditor().defaultState()),
            stop = EditorRoot(CodeBlockEditor().defaultState()),
            noteOn = EditorRoot(CodeBlockEditor().defaultState()),
            noteOff = EditorRoot(CodeBlockEditor().defaultState()),
            cc = EditorRoot(CodeBlockEditor().defaultState())
        ).withName(name)
    }
}