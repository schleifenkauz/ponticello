package ponticello.sc

import hextant.codegen.Compound
import hextant.context.Context
import ponticello.sc.client.ScWriter

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

@Compound(nodeType = ScExpr::class, classLocation = "ponticello.sc.editor.TaskExprEditor")
fun task(block: CodeBlock): ScExpr {
    val task = Identifier("Task").send("new", block).send("play")
    return Identifier("~tasks").send("add", task)
}

@Compound(nodeType = ScExpr::class)
fun functionDef(name: Identifier, parameters: List<Identifier>, body: CodeBlock): ScExpr =
    Assignment(Identifier("~${name.text}"), ScFunction(parameters, body))

@Compound(nodeType = ScExpr::class)
fun transformSignal(bus: ScExpr, mix: ScExpr, signalVar: Identifier, body: CodeBlock): ScExpr =
    bus.send("transformSignal", mix, ScFunction(listOf(signalVar), body))

@Compound(nodeType = ScExpr::class)
data class PlayObject(val scoreObjectNameExpr: ScExpr) : ScExpr {
    override val isValid: Boolean
        get() = scoreObjectNameExpr.isValid

    override fun code(writer: ScWriter, context: Context) {
        val timestamp = "TempoClock.beats"
        writer.append("~ponticello_addr.sendMsg(\\play, -1, ${scoreObjectNameExpr.code(context)}, $timestamp, player_id)")
    }
}