package ponticello.sc.editor

import bundles.getOrNull
import bundles.set
import hextant.context.Context
import hextant.context.extend
import hextant.core.Editor
import hextant.core.editor.Expander
import hextant.core.editor.ExpanderConfig
import hextant.core.editor.isSubEditor
import ponticello.model.ctx.PonticelloContext
import ponticello.model.ctx.Scope
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
        condition = { exp ->
            val ctx = exp.context.getOrNull(PonticelloContext)
            ctxClass.isInstance(ctx)
        },
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

fun Context.makeSubScope(variables: IdentifierListEditor, type: String): Context = extend {
    val scope = Scope.fromList(variables.editors, parent = get(Scope)) { editor ->
        BoundIdentifier(editor, type)
    }
    set(Scope, scope)
}