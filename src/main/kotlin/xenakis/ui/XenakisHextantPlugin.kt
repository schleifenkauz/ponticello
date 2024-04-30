package xenakis.ui

import hextant.context.ControlFactory
import hextant.plugins.Aspects
import hextant.plugins.PluginBuilder
import hextant.plugins.PluginInitializer
import hextant.plugins.stylesheet
import xenakis.sc.editor.ScExprExpander

object XenakisHextantPlugin: PluginInitializer({
    stylesheet("xenakis/ui/style.css")
    on(PluginBuilder.Phase.Initialize) { ctx ->
        ctx[Aspects].implement(ControlFactory::class, ScExprExpander::class, ScExprExpanderControlFactory)
    }
})