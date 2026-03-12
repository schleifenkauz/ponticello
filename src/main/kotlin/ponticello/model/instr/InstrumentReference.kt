package ponticello.model.instr

import hextant.context.Context
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import ponticello.model.flow.VSTPluginFlow
import ponticello.model.obj.VSTPluginReference
import ponticello.model.obj.project
import ponticello.model.project.PonticelloProject
import ponticello.model.project.flows
import ponticello.model.project.instruments
import ponticello.model.registry.ObjectReference
import ponticello.model.registry.reference
import reaktive.value.ReactiveString
import reaktive.value.reactiveValue

@Serializable(with = InstrumentReference.Serializer::class)
sealed interface InstrumentReference {
    val name: ReactiveString

    fun getName(): String

    fun get(): InstrumentObject?

    data class UserDefined(val reference: ObjectReference<InstrumentObject>) : InstrumentReference {
        override val name: ReactiveString get() = reference.name

        override fun getName(): String = reference.getName()

        override fun get(): InstrumentObject? = reference.get()
    }

    data class VST(val flow: VSTPluginReference) : InstrumentReference {
        override fun getName(): String = flow.getName()

        override val name: ReactiveString get() = flow.name

        override fun get(): InstrumentObject? {
            val flow = flow.get() ?: return null
            return VSTInstrumentObject(flow)
        }
    }

    data object None : InstrumentReference {
        override val name: ReactiveString get() = reactiveValue("none")

        override fun getName(): String = "None"

        override fun get(): InstrumentObject? = null
    }

    fun resolve(context: Context) {
        when (this) {
            is UserDefined -> reference.resolve(context.project.instruments)
            is VST -> flow.resolve(context.project.flows.allFlows().filterIsInstance<VSTPluginFlow>())
            None -> {}
        }
    }

    object Serializer : KSerializer<InstrumentReference> {
        override val descriptor: SerialDescriptor
            get() = serialDescriptor<String>()

        override fun serialize(encoder: Encoder, value: InstrumentReference) {
            val str = when (value) {
                is UserDefined -> value.getName()
                is VST -> "VST:${value.getName()}"
                None -> "<none>"
            }
            encoder.encodeString(str)
        }

        override fun deserialize(decoder: Decoder): InstrumentReference {
            val str = decoder.decodeString()
            return when {
                str == "<none>" -> None
                str.startsWith("VST:") -> {
                    val flowName = str.removePrefix("VST:")
                    VST(VSTPluginReference(flowName))
                }

                else -> UserDefined(ObjectReference(str))
            }
        }
    }

    companion object {
        fun getOptions(project: PonticelloProject, midi: Boolean = false): List<InstrumentReference> {
            var userInstruments: List<InstrumentObject> = project.instruments
            if (midi) {
                userInstruments = userInstruments.filter { def ->
                    def.hasParameter("freq") || def.hasParameter("midinote")
                }
            }
            val vstInstruments = project.flows.allFlows()
                .filterIsInstance<VSTPluginFlow>()
                //.filter { flow -> flow.supportsMidiInput }
                .map { flow -> VST(flow.reference()) }
            return userInstruments.map { def -> UserDefined(def.reference()) } + vstInstruments
        }
    }
}