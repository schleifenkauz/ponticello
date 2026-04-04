package ponticello.model.tree

import hextant.context.Context
import javafx.scene.paint.Color
import ponticello.model.flow.AudioFlowGroup
import ponticello.model.score.ObjectPosition
import ponticello.model.score.SoundProcess
import reaktive.value.ReactiveString
import reaktive.value.ReactiveValue

sealed interface AudioNode {
    val name: ReactiveString
    val scoreY: Float
    val nodeID: Int
    val context: Context
    val associatedColor: ReactiveValue<Color?>

    data class SoundProcessInstance(
        override val nodeID: Int,
        val process: SoundProcess, val position: ObjectPosition
    ) : AudioNode {
        override val name: ReactiveString
            get() = process.name
        override val scoreY: Float
            get() = position.y.toFloat()
        override val context: Context
            get() = process.context
        override val associatedColor: ReactiveValue<Color?>
            get() = process.associatedColor
    }

    data class FlowGroup(
        override val nodeID: Int,
        val group: AudioFlowGroup, override var scoreY: Float
    ) : AudioNode {
        override val name: ReactiveString
            get() = group.name

        override val context: Context
            get() = group.context

        override val associatedColor: ReactiveValue<Color?>
            get() = group.associatedColor
    }
}