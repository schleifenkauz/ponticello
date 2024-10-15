package xenakis.ui.controls

import xenakis.model.score.KnobControl

interface KnobControlView {
    fun updatedValue(control: KnobControl, value: Double)
}