package xenakis.sc

import hextant.codegen.Component
import hextant.codegen.Compound
import hextant.context.Context
import reaktive.value.now
import xenakis.impl.Logger
import xenakis.model.obj.ScoreObjectReference
import xenakis.sc.client.ScWriter
import xenakis.sc.editor.ScoreObjectSelector

@Compound(nodeType = ScExpr::class)
fun IfExpr(condition: ScExpr, then: ScExpr, otherwise: ScExpr): ScExpr =
    condition.send("if", lambda(then), lambda(otherwise))

@Compound(nodeType = ScExpr::class)
fun WhileExpr(condition: ScExpr, block: CodeBlock): ScExpr {
    val condFunc = ScFunction(body = CodeBlock(emptyList(), statements = listOf(condition)))
    val bodyFunc = ScFunction(body = block)
    return condFunc.send("while", bodyFunc)
}

@Compound(nodeType = ScExpr::class)
fun LoopExpr(block: CodeBlock): ScExpr = ScFunction(body = block).send("loop")

@Compound(nodeType = ScExpr::class)
fun SynthExpr(synthDef: Identifier, arguments: List<NamedExpr>): ScExpr {
    val synth = Identifier("Synth").send("new", RawScExpr("\\${synthDef.text}"), ArrayExpr(arguments))
        .send("onFree", lambda("synth") { Identifier("~synths").send("remove", Identifier("synth")) })
    return Identifier("~synths").send("add", synth)
}

@Compound(nodeType = ScExpr::class, classLocation = "xenakis.sc.editor.TaskExprEditor")
fun task(block: CodeBlock): ScExpr {
    val task = Identifier("Task").send("new", block).send("play")
    return Identifier("~tasks").send("add", task)
}

@Compound(nodeType = ScExpr::class)
fun functionDef(name: Identifier, parameters: List<Identifier>, body: CodeBlock): ScExpr =
    Assignment(Identifier("~${name.text}"), ScFunction(parameters, body))

@Compound(nodeType = ScExpr::class)
data class PlayObject(
    @Component(ScoreObjectSelector::class) val reference: ScoreObjectReference,
) : ScExpr {
    override val isValid: Boolean
        get() = reference.isValid

    override fun code(writer: ScWriter, context: Context) {
        val obj = reference.get()
        if (obj == null) {
            Logger.error("PlayObject reference $reference is invalid")
            return
        }
        val name = obj.name.now
        writer.append("~xenakis_addr.sendMsg(\\play, -1, '$name')")
    }
}