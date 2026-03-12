package ponticello.model.tree

import hextant.context.Context
import ponticello.model.flow.AudioFlowGroup
import ponticello.model.score.ObjectPosition
import ponticello.model.score.SoundProcess
import reaktive.value.ReactiveString

sealed interface AudioNode {
    val name: ReactiveString
    val context: Context

    data class SoundProcessInstance(val process: SoundProcess, val position: ObjectPosition) : AudioNode {
        override val name: ReactiveString
            get() = process.name
        override val context: Context
            get() = process.context
    }

    data class FlowGroup(val group: AudioFlowGroup) : AudioNode {
        override val name: ReactiveString
            get() = group.name

        override val context: Context
            get() = group.context
    }
}