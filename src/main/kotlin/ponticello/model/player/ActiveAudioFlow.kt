package ponticello.model.player

import ponticello.model.flow.AudioFlow
import ponticello.model.instr.ParameterizedObject

class ActiveAudioFlow(val flow: AudioFlow) : ActiveObject() {
    override val associatedObject: ParameterizedObject?
        get() = flow as? ParameterizedObject

    override val superColliderName get() = flow.superColliderName

    override val uniqueName: String
        get() = superColliderName.removePrefix("~")
}