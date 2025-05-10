package ponticello.model.player

import ponticello.model.flow.AudioFlow
import ponticello.model.obj.ParameterizedObject

class ActiveAudioFlow(val flow: AudioFlow) : ActiveObject() {
    override val associatedObject: ParameterizedObject?
        get() = flow as? ParameterizedObject

    override val superColliderName: String
        get() = flow.superColliderName

    override val uniqueName: String
        get() = superColliderName.removePrefix("~")
}