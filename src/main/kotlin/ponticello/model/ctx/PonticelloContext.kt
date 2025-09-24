package ponticello.model.ctx

import bundles.PublicProperty
import bundles.publicProperty
import ponticello.model.obj.ConfigurableInstrumentObject
import ponticello.model.obj.CustomizableSynthDefObject
import ponticello.model.obj.ProcessDefObject
import ponticello.model.score.ParameterControlList

sealed class PonticelloContext {
    sealed class InstrumentDef : PonticelloContext() {
        abstract val def: ConfigurableInstrumentObject
    }

    data class SynthDef(override val def: CustomizableSynthDefObject) : InstrumentDef()

    data class ProcessDef(override val def: ProcessDefObject) : InstrumentDef()

    data class Control(val control: ParameterControlList.NamedParameterControl) : PonticelloContext()

    companion object : PublicProperty<PonticelloContext> by publicProperty("PonticelloContext")
}