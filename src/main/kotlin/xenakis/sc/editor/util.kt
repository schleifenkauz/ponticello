package xenakis.sc.editor

import hextant.core.editor.ColorEditor
import xenakis.model.obj.BusObject
import xenakis.model.obj.BusReference
import xenakis.model.registry.ObjectReference
import xenakis.model.registry.reference
import xenakis.sc.NumericalControlSpec
import xenakis.sc.Rate

fun NumericalControlSpec.createEditor() = NumericalControlSpecEditor(
    defaultValue = DecimalLiteralEditor(defaultValue.text),
    min = DecimalLiteralEditor(min.text),
    max = DecimalLiteralEditor(max.text),
    warp = WarpEditor(warp),
    step = DecimalLiteralEditor(step.text),
    associatedColor = ColorEditor(associatedColor)
)

private fun ScExprEditor<*>.exp() = ScExprExpander(this)

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
    AssignmentEditor(IdentifierEditor(name), expr).exp()
