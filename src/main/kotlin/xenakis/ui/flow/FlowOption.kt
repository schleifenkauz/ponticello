package xenakis.ui.flow

import hextant.context.Context
import javafx.scene.layout.Region
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.model.flow.*
import xenakis.model.obj.BusObject
import xenakis.model.obj.GroupObject
import xenakis.model.obj.SynthDefObject
import xenakis.model.registry.BusRegistry
import xenakis.model.registry.GroupRegistry
import xenakis.model.registry.InstrumentRegistry
import xenakis.model.registry.reference
import xenakis.ui.controls.NamePrompt
import xenakis.ui.registry.SimpleSearchableRegistryView

sealed interface FlowOption {
    fun createFlow(context: Context, anchor: Region, associatedBus: BusObject, onCreate: (AudioFlow) -> Unit)

    data object Send : FlowOption {
        override fun createFlow(
            context: Context,
            anchor: Region,
            associatedBus: BusObject,
            onCreate: (AudioFlow) -> Unit
        ) {
            SimpleSearchableRegistryView(context[BusRegistry], "Target bus")
                .showPopup(anchor) { selected ->
                    onCreate(SendFlow.createFor(associatedBus, selected, context))
                }

        }
    }

    data object Utility : FlowOption {
        override fun createFlow(
            context: Context,
            anchor: Region,
            associatedBus: BusObject,
            onCreate: (AudioFlow) -> Unit
        ) = onCreate(UtilityFlow())
    }

    data object Code : FlowOption {
        override fun createFlow(
            context: Context,
            anchor: Region,
            associatedBus: BusObject,
            onCreate: (AudioFlow) -> Unit
        ) = onCreate(CodeFlow.createFor(associatedBus, context))
    }

    data object Placeholder : FlowOption {
        override fun createFlow(
            context: Context,
            anchor: Region,
            associatedBus: BusObject,
            onCreate: (AudioFlow) -> Unit
        ) {
            val groupName = NamePrompt(context[GroupRegistry], "Group name", "").showDialog(anchor) ?: return
            val group = GroupObject(reactiveVariable(groupName))
            context[GroupRegistry].add(group)
            onCreate(ScoreObjectPlaceholder(group.reference()))
        }
    }

    data class Synth(val def: SynthDefObject) : FlowOption {
        override fun createFlow(
            context: Context,
            anchor: Region,
            associatedBus: BusObject,
            onCreate: (AudioFlow) -> Unit
        ) = onCreate(SynthFlow.createFor(associatedBus, def, context))

        override fun toString(): String = "Synth ${def.name.now}"
    }

    companion object {
        private val simpleOptions = listOf(Code, Send, Utility, Placeholder)

        fun getOptions(context: Context) =
            simpleOptions + context[InstrumentRegistry].all().filterIsInstance<SynthDefObject>().map(::Synth)
    }
}