package xenakis.sc

import java.util.*

inline fun <reified R> ScElement.allChildren(): List<R> = buildList {
    val queue: Queue<ScElement> = LinkedList()
    queue.offer(this@allChildren)
    while (queue.isNotEmpty()) {
        val element = queue.poll()
        if (element is R) add(element)
        for (child in element.children) {
            queue.offer(child)
        }
    }
}

fun ScElement.visit(visitor: (ScElement) -> Unit) {
    visitor(this)
    for (element in children) visitor(element)
}

fun ScExpr.transform(f: (ScExpr) -> ScExpr): ScExpr = when (this) {
    is ArrayExpr -> f(ArrayExpr(elements.map { e -> e.transform(f) }))
    is NamedExpr -> f(NamedExpr(name, value.transform(f)))
    is CodeBlock -> f(transformBlock(f))
    is ScFunction -> f(ScFunction(parameters, body.transformBlock(f)))
    is Assignment -> f(Assignment(variable, expression.transform(f)))
    is MessageSend -> f(MessageSend(receiver.transform(f), method, arguments.map { a -> a.transform(f) }))
    is OperatorExpr -> f(OperatorExpr(left.transform(f), operator, right.transform(f)))
    is NewObject -> f(NewObject(className, arguments.map { a -> a.transform(f) }))
    is AccessKey -> f(AccessKey(receiver.transform(f), key.transform(f)))
    is SpreadArray -> f(SpreadArray(array.transform(f)))
    is VSTPlugin -> f(VSTPlugin(input.transform(f), channels, pluginName, id, presetName))
    is AdhocSynth -> f(AdhocSynth(name, block.transformBlock(f), group))
    else -> f(this)
}

@JvmName("transformGeneric")
inline fun <reified E : ScExpr> ScExpr.transform(crossinline f: (E) -> ScExpr) =
    transform { e -> if (e is E) f(e) else e }

fun ScExpr.substitute(name: String, expr: ScExpr) = transform { e -> if (e == Identifier(name)) expr else e }

private fun CodeBlock.transformBlock(f: (ScExpr) -> ScExpr) =
    CodeBlock(variables, statements.map { s -> s.transform(f) })
