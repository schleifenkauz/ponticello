package xenakis.ui

import xenakis.model.XenakisProject
import xenakis.sc.SynthDef

class SynthDefsEditor(private var project: XenakisProject) {
    val selectedSynthDef: SynthDef get() = SynthDef.default
}