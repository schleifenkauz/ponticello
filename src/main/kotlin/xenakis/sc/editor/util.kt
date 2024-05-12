package xenakis.sc.editor

import hextant.context.Context
import hextant.core.editor.ColorEditor
import xenakis.sc.NumericalControlSpec

fun NumericalControlSpec.createEditor(context: Context) = NumericalControlSpecEditor(
    context,
    defaultValue = DoubleLiteralEditor(context, defaultValue.text),
    min = DoubleLiteralEditor(context, min.text),
    max = DoubleLiteralEditor(context, max.text),
    warp = WarpEditor(context, warp),
    step = DoubleLiteralEditor(context, step.text),
    associatedColor = ColorEditor(context, associatedColor)
)
