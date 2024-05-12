package xenakis.sc

import hextant.codegen.Compound

@Compound(nodeType = ScExpr::class, serializable = true)
fun IfExpr(condition: ScExpr, then: ScExpr, otherwise: ScExpr): ScExpr =
    condition.send("if", lambda(then), lambda(otherwise))

@Compound(nodeType = ScExpr::class, serializable = true)
fun WhileExpr(condition: ScExpr, block: CodeBlock): ScExpr {
    val condFunc = ScFunction(body = CodeBlock(emptyList(), statements = listOf(condition)))
    val bodyFunc = ScFunction(body = block)
    return condFunc.send("while", bodyFunc)
}