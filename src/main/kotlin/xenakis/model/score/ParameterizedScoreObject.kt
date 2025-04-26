package xenakis.model.score

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.ReactiveValue
import xenakis.impl.Decimal
import xenakis.model.obj.ParameterizedObject
import xenakis.model.player.LiveSynthUpdater
import xenakis.model.score.controls.ParameterControl
import xenakis.sc.ControlSpec
import xenakis.ui.misc.LFOsManager

@Serializable
sealed class ParameterizedScoreObject : ScoreObject(), ParameterizedObject {
    @Transient
    private lateinit var controlListener: LiveSynthUpdater

    @Transient
    lateinit var lfosManager: LFOsManager
        private set

    override val associatedControls: Map<String, ParameterControl>
        get() = controls.controlMap

    abstract override val superColliderPrefix: String

    override fun duration(): ReactiveValue<Decimal> = super<ScoreObject>.duration()

    override fun getSpec(parameter: String): ControlSpec? = super<ParameterizedObject>.getSpec(parameter)

    protected fun initializeControls() {
        controls.initialize(context, this)
        controlListener = LiveSynthUpdater(this)
        lfosManager = LFOsManager()
    }

    override fun onLoadedIntoRegistry() {
        super<ScoreObject>.onLoadedIntoRegistry()
        controlListener.startListening()
        controls.addListener(lfosManager)
    }

    override fun onRemoved() {
        super<ScoreObject>.onRemoved()
        controlListener.stopListening()
        controls.removeListener(lfosManager)
    }
}