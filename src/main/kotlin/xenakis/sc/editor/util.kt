package xenakis.sc.editor

import hextant.context.Context
import hextant.core.editor.ColorEditor
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.model.BusObject
import xenakis.model.ObjectReference
import xenakis.sc.NumericalControlSpec
import xenakis.sc.Rate

fun NumericalControlSpec.createEditor(context: Context) = NumericalControlSpecEditor(
    context,
    defaultValue = DoubleLiteralEditor(context, defaultValue.text),
    min = DoubleLiteralEditor(context, min.text),
    max = DoubleLiteralEditor(context, max.text),
    warp = WarpEditor(context, warp),
    step = DoubleLiteralEditor(context, step.text),
    associatedColor = ColorEditor(context, associatedColor)
)

fun ScExprEditor<*>.exp() = ScExprExpander(context, this)

fun simpleText(context: Context, text: String) = ScExprExpander(context, text)

fun out(context: Context, outputBus: BusObject, snd: ScExprExpander): ScExprExpander {
    val outExpr =
        ScExprExpander(context, BusSelector(context, selected = reactiveVariable(outputBus.createReference())))
    return out(context, outExpr, snd, outputBus.rate.now)
}

fun out(
    context: Context,
    bus: ScExprExpander,
    snd: ScExprExpander,
    rate: Rate
) = MessageSendEditor(
    context,
    ScExprExpander(context, "Out"),
    method = IdentifierEditor(context, rate.toString()),
    arguments = ScExprListEditor(
        context,
        bus,
        snd
    )
).exp()

fun `in`(context: Context, inputBus: BusObject): ScExprExpander {
    val bus = BusSelector(context, selected = reactiveVariable(inputBus.createReference()))
    return `in`(context, ScExprExpander(context, bus), inputBus.rate.get(), inputBus.channels.now)
}

fun `in`(context: Context, bus: ScExprExpander, rate: Rate, channels: Int) = ScExprExpander(
    context, MessageSendEditor(
        context,
        ScExprExpander(context, "In"),
        IdentifierEditor(context, rate.toString()),
        ScExprListEditor(
            context,
            bus,
            ScExprExpander(context, channels.toString())
        )
    )
)

fun selectSubBus(context: Context, busRef: ObjectReference, idx: Int): ScExprExpander = MessageSendEditor(
    context, ScExprExpander(context, "Bus"),
    method = IdentifierEditor(context, "newFrom"),
    arguments = ScExprListEditor(
        context,
        ScExprExpander(
            context,
            BusSelector(context, selected = reactiveVariable(busRef))
        ),
        ScExprExpander(context, "${idx - 1}"),
        ScExprExpander(context, "1")
    )
).exp()

fun assign(name: String, expr: ScExprExpander) =
    AssignmentEditor(expr.context, IdentifierEditor(expr.context, name), expr).exp()
