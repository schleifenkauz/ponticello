package xenakis.sc

import hextant.codegen.Compound

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
fun SynthExpr(synthDef: Identifier, arguments: List<NamedExpr>): ScExpr =
    Identifier("Synth").send("new", SymbolLiteral(synthDef.text), ArrayExpr(arguments))