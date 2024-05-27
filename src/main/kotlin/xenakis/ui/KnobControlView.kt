package xenakis.ui

import xenakis.model.KnobControl

interface KnobControlView {
    fun updatedValue(control: KnobControl, value: Double)
}