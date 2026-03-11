package ponticello.sc.editor

import hextant.core.Editor
import hextant.core.editor.Expander
import hextant.core.editor.ExpanderConfig
import hextant.core.editor.isSubEditor
import ponticello.model.ctx.PonticelloContext
import ponticello.model.instr.BusObject
import ponticello.model.registry.reference
import ponticello.sc.Rate
import kotlin.reflect.KClass
import kotlin.reflect.cast

private fun ScExprEditor<*>.exp() = ScExprExpander(this)

fun Expander<*, *>.isStatementInBlock(): Boolean {
    val parent = parent
    if (parent !is ScExprListEditor) return false
    return parent.isSubEditor(CodeBlockEditor::statements)
}

fun <C : PonticelloContext, E : Editor<*>> ExpanderConfig<E>.expandInContext(
    keyword: String, ctxClass: KClass<C>, create: (Expander<*, *>, ctx: C) -> E?
) {
    keyword.expand(
        condition = { exp -> ctxClass.isInstance(exp.context[PonticelloContext]) },
        create = { exp ->
            val ctx = ctxClass.cast(exp.context[PonticelloContext])
            create(exp, ctx)
        }
    )
}

fun simpleText(text: String) = ScExprExpander(text)

fun out(outputBus: BusObject, snd: ScExprExpander): ScExprExpander {
    val editor = OutExprEditor(channelsArray = snd)
    editor.busSelector.select(outputBus.reference())
    return editor.exp()
}

fun out(
    bus: ScExprExpander,
    snd: ScExprExpander,
    rate: Rate
) = MessageSendEditor(
    ScExprExpander("Out"),
    method = IdentifierEditor(rate.toString()),
    arguments = ScExprListEditor(bus, snd)
).exp()

fun `in`(inputBus: BusObject): ScExprExpander =
    InExprEditor().apply { busSelector.select(inputBus.reference()) }.exp()

fun `in`(bus: ScExprExpander, rate: Rate, channels: Int) = ScExprExpander(
    MessageSendEditor(
        ScExprExpander("In"),
        IdentifierEditor(rate.toString()),
        ScExprListEditor(
            bus,
            ScExprExpander(channels.toString())
        )
    )
)

fun assign(name: String, expr: ScExprExpander) =
    AssignmentEditor(AssignableExprExpander(name), expr).exp()
