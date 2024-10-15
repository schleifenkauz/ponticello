package xenakis.ui.registry

import xenakis.model.registry.GlobalControls

interface GlobalControlsView {
    fun addedControl(control: GlobalControls.GlobalControl)

    fun removedControl(control: GlobalControls.GlobalControl)
}