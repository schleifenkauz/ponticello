package xenakis.sc

fun ScElement.visit(visitor: (ScElement) -> Unit) {
    visitor(this)
    when (this) {
        is AccessKey -> visitor(key)
        is ArrayExpr -> elements.forEach { it.visit(visitor) }
        is Assignment -> {
            variable.visit(visitor)
            expression.visit(visitor)
        }

        is CodeBlock -> {
            variables.forEach { it.visit(visitor) }
            statements.forEach { it.visit(visitor) }
        }

        is LiteralArray -> elements.forEach { it.visit(visitor) }
        is TupleExpr -> elements.forEach { it.visit(visitor) }
        is MessageSend -> {
            receiver.visit(visitor)
            method.visit(visitor)
            arguments.forEach { it.visit(visitor) }
        }

        is NamedExpr -> {
            name.visit(visitor)
            value.visit(visitor)
        }

        is NewObject -> {
            className.visit(visitor)
            arguments.forEach { it.visit(visitor) }
        }

        is OperatorExpr -> {
            left.visit(visitor)
            operator.visit(visitor)
            right.visit(visitor)
        }

        is ScFunction -> {
            parameters.forEach { it.visit(visitor) }
            body.visit(visitor)
        }

        is SpreadArray -> {
            array.visit(visitor)
        }

        else -> {}
    }
}

data class VstPluginUse(val id: String?)

fun ScExpr.vstPluginUses(): List<VstPluginUse> = buildList {
    visit { element ->
        if (element is MessageSend && element.receiver == Identifier("VSTPlugin") && element.method.text == "ar") {
            val id = element.arguments.filterIsInstance<NamedExpr>().find { it.name.text == "id" }?.value as? Identifier
            add(VstPluginUse(id?.text))
        }
    }
}