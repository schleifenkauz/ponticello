package xenakis.model

interface GlobalControlsView {
    fun addedControl(control: GlobalControls.GlobalControl)

    fun removedControl(control: GlobalControls.GlobalControl)
}