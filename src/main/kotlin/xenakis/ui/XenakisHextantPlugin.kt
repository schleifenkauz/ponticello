package xenakis.ui

import hextant.plugins.PluginInitializer
import hextant.plugins.stylesheet

object XenakisHextantPlugin: PluginInitializer({
    stylesheet("xenakis/ui/style.css")
})