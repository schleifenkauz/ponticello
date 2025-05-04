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
    is Assignment -> f(Assignment(assignee, expression.transform(f))) //TODO how to transform assignee
    is MessageSend -> f(MessageSend(receiver.transform(f), method, arguments.map { a -> a.transform(f) }))
    is OperatorExpr -> f(OperatorExpr(left.transform(f), operator, right.transform(f)))
    is AccessKey -> f(AccessKey(receiver.transform(f), key.transform(f)))
    is SpreadArray -> f(SpreadArray(array.transform(f)))
    is VSTPlugin -> f(VSTPlugin(input.transform(f), channels, pluginName, id, presetName))
    is AdhocSynth -> f(AdhocSynth(name, block.transformBlock(f)))
    else -> f(this)
}

@JvmName("transformGeneric")
inline fun <reified E : ScExpr> ScExpr.transform(crossinline f: (E) -> ScExpr) =
    transform { e -> if (e is E) f(e) else e }

fun ScExpr.substitute(map: Map<String, () -> ScExpr>): ScExpr = transform { e ->
    when {
        e is Identifier && e.text in map -> map.getValue(e.text).invoke()
        e is CodeBlock -> {
            val shadowedVariables = e.variables.mapTo(mutableSetOf()) { v -> v.text }
            val shadowedMap = map - shadowedVariables
            CodeBlock(e.variables, e.statements.map { s -> s.substitute(shadowedMap) })
        }

        e is ScFunction -> {
            val shadowedVariables =
                e.parameters.mapTo(mutableSetOf()) { v -> v.text } + e.body.variables.mapTo(mutableSetOf()) { v -> v.text }
            val shadowedMap = map - shadowedVariables
            ScFunction(
                e.parameters,
                CodeBlock(e.body.variables, e.body.statements.map { s -> s.substitute(shadowedMap) })
            )
        }

        else -> e
    }
}

private fun CodeBlock.transformBlock(f: (ScExpr) -> ScExpr) =
    CodeBlock(variables, statements.map { s -> s.transform(f) })
