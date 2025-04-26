package xenakis.model.score.controls

import xenakis.model.score.ParameterControlList

class LFOLiveUpdater : ParameterControlList.Listener {
    override fun added(obj: ParameterControlList.NamedParameterControl, idx: Int) {
        super.added(obj, idx)
    }

    override fun removed(obj: ParameterControlList.NamedParameterControl) {
        super.removed(obj)
    }

    override fun reassignedControl(
        namedControl: ParameterControlList.NamedParameterControl,
        oldControl: ParameterControl,
        control: ParameterControl,
    ) {
        TODO("Not yet implemented")
    }
}